package com.najacks.backend.domain.stream.service;

import com.najacks.backend.domain.stream.entity.LiveStatus;
import com.najacks.backend.domain.stream.entity.StreamerLiveHistory;
import com.najacks.backend.domain.stream.entity.StreamerLiveState;
import com.najacks.backend.domain.stream.event.StreamEndedEvent;
import com.najacks.backend.domain.stream.event.StreamStartedEvent;
import com.najacks.backend.domain.stream.external.ChzzkLiveDetail;
import com.najacks.backend.domain.stream.repository.StreamerLiveHistoryRepository;
import com.najacks.backend.domain.stream.repository.StreamerLiveStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class StreamStateDiffService {

    /** 연속 N회 OFFLINE으로 관측되어야 실제 방종 확정. 치지직 lives 응답 순간 누락 방어. */
    private static final int OFFLINE_CONFIRM_THRESHOLD = 2;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final StreamerLiveStateRepository stateRepo;
    private final StreamerLiveHistoryRepository historyRepo;
    private final ApplicationEventPublisher publisher;

    @Transactional
    public void process(StreamerLiveState state, ChzzkLiveDetail detail) {
        state.setLastCheckedAt(LocalDateTime.now(SEOUL));
        if (detail == null) {
            state.setFailureCount(state.getFailureCount() + 1);
            stateRepo.save(state);
            return;
        }
        state.setFailureCount(0);

        LiveStatus prev = state.getCurrentLiveStatus() == null ? LiveStatus.UNKNOWN : state.getCurrentLiveStatus();
        boolean observedLive = detail.isLive();

        if (observedLive && prev != LiveStatus.LIVE) {
            startLive(state, detail);
        } else if (!observedLive && prev == LiveStatus.LIVE) {
            handlePossibleEnd(state);
        } else if (observedLive) {
            continueLive(state, detail);
        } else {
            state.setOfflineMissCount(0);
            stateRepo.save(state);
        }
    }

    private void startLive(StreamerLiveState state, ChzzkLiveDetail detail) {
        LocalDateTime realStart = parseChzzkOpenDate(detail.getOpenDate());
        if (realStart == null) realStart = LocalDateTime.now(SEOUL);
        state.setCurrentLiveStatus(LiveStatus.LIVE);
        state.setCurrentStreamId(detail.getLiveId());
        state.setCurrentStreamTitle(detail.getLiveTitle());
        state.setCurrentStreamCategory(detail.getLiveCategoryValue());
        state.setCurrentViewerCount(detail.getConcurrentUserCount());
        state.setCurrentPeakViewerCount(detail.getConcurrentUserCount());
        state.setCurrentStreamStartedAt(realStart);
        state.setLastLiveAt(LocalDateTime.now(SEOUL));
        state.setOfflineMissCount(0);
        stateRepo.save(state);
        publisher.publishEvent(new StreamStartedEvent(state.getStreamerNo(), detail));
        log.info("방송 시작 streamerNo={} title={}", state.getStreamerNo(), detail.getLiveTitle());
    }

    private void continueLive(StreamerLiveState state, ChzzkLiveDetail detail) {
        state.setCurrentStreamTitle(detail.getLiveTitle());
        state.setCurrentStreamCategory(detail.getLiveCategoryValue());
        state.setCurrentViewerCount(detail.getConcurrentUserCount());
        state.setLastLiveAt(LocalDateTime.now(SEOUL));
        state.setOfflineMissCount(0);

        Integer curr = detail.getConcurrentUserCount();
        Integer peak = state.getCurrentPeakViewerCount();
        if (curr != null && (peak == null || curr > peak)) {
            state.setCurrentPeakViewerCount(curr);
        }
        stateRepo.save(state);

        // live_histories.peak 도 동기화 (리포트 소스)
        if (state.getCurrentStreamId() != null && curr != null) {
            historyRepo.findByStreamId(state.getCurrentStreamId()).ifPresent(h -> {
                Integer hp = h.getPeakViewerCount();
                if (hp == null || curr > hp) {
                    h.setPeakViewerCount(curr);
                    historyRepo.save(h);
                }
            });
        }
    }

    /**
     * OFFLINE 관측 1회로는 종료 확정 안 함. 연속 N회 쌓여야 실제 방종 이벤트 발행.
     * 치지직 /open/v1/lives 목록이 5000+ 채널 중 일시적으로 누락할 수 있음에 대한 방어.
     */
    private void handlePossibleEnd(StreamerLiveState state) {
        int miss = (state.getOfflineMissCount() == null ? 0 : state.getOfflineMissCount()) + 1;
        state.setOfflineMissCount(miss);

        if (miss < OFFLINE_CONFIRM_THRESHOLD) {
            stateRepo.save(state);
            log.info("방종 의심 (miss={}/{}) — 확정 보류 streamerNo={}",
                    miss, OFFLINE_CONFIRM_THRESHOLD, state.getStreamerNo());
            return;
        }

        // 종료 확정
        String endedStreamId = state.getCurrentStreamId();
        Integer finalPeak = state.getCurrentPeakViewerCount();
        state.setCurrentLiveStatus(LiveStatus.OFFLINE);
        state.setCurrentStreamId(null);
        state.setCurrentStreamTitle(null);
        state.setCurrentStreamCategory(null);
        state.setCurrentViewerCount(null);
        state.setCurrentPeakViewerCount(null);
        state.setCurrentStreamStartedAt(null);
        state.setOfflineMissCount(0);
        stateRepo.save(state);

        // live_histories.peak 최종 반영 + ended_at 기록
        if (endedStreamId != null) {
            historyRepo.findByStreamId(endedStreamId).ifPresent(h -> {
                if (finalPeak != null) {
                    Integer hp = h.getPeakViewerCount();
                    if (hp == null || finalPeak > hp) h.setPeakViewerCount(finalPeak);
                }
                historyRepo.save(h);
            });
        }

        publisher.publishEvent(new StreamEndedEvent(state.getStreamerNo(), endedStreamId));
        log.info("방송 종료 확정 streamerNo={} finalPeak={}", state.getStreamerNo(), finalPeak);
    }

    private static final DateTimeFormatter CHZZK_OPEN_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private LocalDateTime parseChzzkOpenDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDateTime.parse(s.trim(), CHZZK_OPEN_DATE_FMT); }
        catch (Exception e) { return null; }
    }
}
