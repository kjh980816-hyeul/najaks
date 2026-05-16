package com.najacks.backend.domain.chat.service;

import com.najacks.backend.domain.chat.chzzk.ChzzkChatMessage;
import com.najacks.backend.domain.chat.entity.ChatHighlight;
import com.najacks.backend.domain.chat.repository.ChatHighlightRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 채팅 CPS 이상치 탐지 — 최근 1분 속도가 직전 19분 평균의 2.5배 이상이면 하이라이트.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HighlightDetector {

    private static final double SPIKE_THRESHOLD = 2.5;
    private static final long LAST_MS = 60_000L;
    private static final long BASELINE_MS = 19 * 60_000L;
    private static final long COOLDOWN_MS = 3 * 60_000L;

    private final HighlightSummarizer summarizer;
    private final ChatHighlightRepository highlightRepo;

    private final java.util.Map<Long, Long> lastHighlightAt = new java.util.concurrent.ConcurrentHashMap<>();

    @Transactional
    public void checkSpike(Long streamerNo, String streamId,
                           Deque<ChzzkChatMessage> window, long nowMs) {
        Long lastAt = lastHighlightAt.get(streamerNo);
        if (lastAt != null && nowMs - lastAt < COOLDOWN_MS) return;

        long lastStart = nowMs - LAST_MS;
        long baseStart = nowMs - LAST_MS - BASELINE_MS;

        long lastCount = 0;
        long baseCount = 0;
        List<ChzzkChatMessage> sample = new ArrayList<>();
        for (ChzzkChatMessage m : window) {
            long t = m.getMessageTime();
            if (t >= lastStart) {
                lastCount++;
                if (sample.size() < 30) sample.add(m);
            } else if (t >= baseStart) {
                baseCount++;
            }
        }

        double baselineAvg = baseCount / 19.0;
        if (baselineAvg < 5) return; // 표본 너무 작음

        double magnitude = lastCount / baselineAvg;
        if (magnitude < SPIKE_THRESHOLD) return;

        lastHighlightAt.put(streamerNo, nowMs);

        String aiSummary;
        try {
            aiSummary = summarizer.summarize(sample);
        } catch (Exception e) {
            log.warn("하이라이트 요약 실패 — 원문 사용 streamerNo={}", streamerNo, e);
            aiSummary = sample.isEmpty() ? "(하이라이트)" : sample.get(0).getContent();
        }

        try {
            highlightRepo.save(ChatHighlight.builder()
                    .streamerNo(streamerNo)
                    .streamId(streamId)
                    .occurredAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), ZoneId.of("Asia/Seoul")))
                    .cpsObserved((float) lastCount / 60f)
                    .cpsBaseline((float) baselineAvg / 60f)
                    .magnitude((float) magnitude)
                    .aiSummary(truncate(aiSummary, 500))
                    .sampleMessages(toJsonSample(sample))
                    .build());
            log.info("🔥 하이라이트 저장 streamerNo={} magnitude={}", streamerNo, String.format("%.2f", magnitude));
        } catch (Exception e) {
            log.warn("하이라이트 저장 실패 streamerNo={}", streamerNo, e);
        }
    }

    private String toJsonSample(List<ChzzkChatMessage> sample) {
        StringBuilder sb = new StringBuilder("[");
        int limit = Math.min(sample.size(), 20);
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append(",");
            ChzzkChatMessage m = sample.get(i);
            sb.append("{\"nick\":\"").append(escape(m.getNickname()))
                    .append("\",\"msg\":\"").append(escape(m.getContent())).append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }

    private String truncate(String s, int limit) {
        if (s == null) return null;
        return s.length() <= limit ? s : s.substring(0, limit);
    }
}
