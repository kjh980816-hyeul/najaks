package com.najacks.backend.domain.chat.service;

import com.najacks.backend.domain.chat.chzzk.ChzzkChatMessage;
import com.najacks.backend.domain.chat.entity.ChatMinuteStats;
import com.najacks.backend.domain.chat.repository.ChatMinuteStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 스트리머별 슬라이딩 윈도우(최근 20분) 버퍼.
 * 매 60초마다 최근 1분 통계를 chat_minute_stats로 저장 + HighlightDetector 호출.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatBufferService {

    private static final long WINDOW_MS = 20 * 60 * 1000L;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int SAMPLE_PER_MINUTE = 10;

    private final ChatMinuteStatsRepository statsRepo;
    private final HighlightDetector detector;

    /** streamerNo → Deque<ChzzkChatMessage> */
    private final Map<Long, Deque<ChzzkChatMessage>> windows = new ConcurrentHashMap<>();
    /** streamerNo → 현재 streamId */
    private final Map<Long, String> activeStreams = new ConcurrentHashMap<>();

    public void register(Long streamerNo, String streamId) {
        activeStreams.put(streamerNo, streamId);
        windows.putIfAbsent(streamerNo, new ConcurrentLinkedDeque<>());
    }

    public void unregister(Long streamerNo) {
        activeStreams.remove(streamerNo);
        windows.remove(streamerNo);
    }

    /** 방송 종료 직전 호출 — 마지막 1분치 통계를 즉시 플러시 후 버퍼 해제 */
    public void flushFinal(Long streamerNo) {
        String streamId = activeStreams.get(streamerNo);
        Deque<ChzzkChatMessage> dq = windows.get(streamerNo);
        if (streamId != null && dq != null && !dq.isEmpty()) {
            try {
                snapshot(streamerNo, streamId, dq, System.currentTimeMillis());
            } catch (Exception e) {
                log.warn("flushFinal 실패 streamerNo={}", streamerNo, e);
            }
        }
        unregister(streamerNo);
    }

    public void accept(Long streamerNo, ChzzkChatMessage msg) {
        Deque<ChzzkChatMessage> dq = windows.get(streamerNo);
        if (dq == null) return;
        dq.add(msg);
    }

    @Scheduled(fixedRate = 60_000)
    public void tick() {
        long now = System.currentTimeMillis();
        long cutoff = now - WINDOW_MS;

        for (Map.Entry<Long, Deque<ChzzkChatMessage>> en : windows.entrySet()) {
            Long streamerNo = en.getKey();
            Deque<ChzzkChatMessage> dq = en.getValue();
            String streamId = activeStreams.get(streamerNo);
            if (streamId == null) continue;

            // 윈도우 밖 메시지 제거
            Iterator<ChzzkChatMessage> it = dq.iterator();
            while (it.hasNext()) {
                if (it.next().getMessageTime() < cutoff) it.remove();
                else break;
            }

            try {
                snapshot(streamerNo, streamId, dq, now);
            } catch (Exception e) {
                log.warn("분당 스냅샷 실패 streamerNo={}", streamerNo, e);
            }

            try {
                detector.checkSpike(streamerNo, streamId, dq, now);
            } catch (Exception e) {
                log.warn("highlight detection 실패 streamerNo={}", streamerNo, e);
            }
        }
    }

    /** 최근 1분 통계 + 대표 메시지 샘플을 chat_minute_stats 1행으로 저장 */
    private void snapshot(Long streamerNo, String streamId, Deque<ChzzkChatMessage> dq, long now) {
        long lastMinStart = now - 60_000;
        int count = 0;
        Set<String> uniq = new HashSet<>();
        List<ChzzkChatMessage> minuteMsgs = new ArrayList<>();
        for (ChzzkChatMessage m : dq) {
            if (m.getMessageTime() >= lastMinStart) {
                count++;
                if (m.getSenderChannelId() != null) uniq.add(m.getSenderChannelId());
                minuteMsgs.add(m);
            }
        }
        if (count == 0) return;

        LocalDateTime bucket = Instant.ofEpochMilli(lastMinStart)
                .atZone(SEOUL).toLocalDateTime()
                .withSecond(0).withNano(0);

        saveStats(streamerNo, streamId, bucket, count, uniq.size(), pickSamples(minuteMsgs));
    }

    /** 분당 메시지에서 고르게 10개 샘플링 (간단한 stride 방식) */
    private String pickSamples(List<ChzzkChatMessage> minuteMsgs) {
        if (minuteMsgs.isEmpty()) return null;
        int size = minuteMsgs.size();
        int take = Math.min(SAMPLE_PER_MINUTE, size);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < take; i++) {
            int idx = (int) ((long) i * size / take);
            ChzzkChatMessage m = minuteMsgs.get(idx);
            if (i > 0) sb.append(",");
            sb.append("{\"n\":\"").append(escape(m.getNickname()))
                    .append("\",\"m\":\"").append(escape(m.getContent())).append("\"}");
        }
        sb.append("]");
        String s = sb.toString();
        return s.length() > 4000 ? s.substring(0, 4000) : s;
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }

    @Transactional
    protected void saveStats(Long streamerNo, String streamId,
                             LocalDateTime bucket, int count, int uniqChatters, String sampleJson) {
        statsRepo.save(ChatMinuteStats.builder()
                .streamerNo(streamerNo)
                .streamId(streamId)
                .minuteBucket(bucket)
                .chatCount(count)
                .uniqueChatters(uniqChatters)
                .sampleMessages(sampleJson)
                .build());
    }
}
