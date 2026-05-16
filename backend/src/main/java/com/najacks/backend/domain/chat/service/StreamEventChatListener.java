package com.najacks.backend.domain.chat.service;

import com.najacks.backend.domain.stream.event.StreamEndedEvent;
import com.najacks.backend.domain.stream.event.StreamStartedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 방송 시작/종료 이벤트를 받아 채팅 수집 세션을 관리한다.
 * StreamAnalysisFinalizer보다 먼저 실행되어야 하는 건 아님 — 각각 독립 리스너.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StreamEventChatListener {

    private final PremiumGateService gate;
    private final ChatCollectorManager collector;

    @Async("aiTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStreamStarted(StreamStartedEvent ev) {
        if (!gate.isEnabled(ev.streamerNo())) return;
        String streamId = ev.detail() != null ? ev.detail().getLiveId() : null;
        if (streamId == null) {
            log.info("streamId 없음 — 채팅 수집 스킵 streamerNo={}", ev.streamerNo());
            return;
        }
        collector.connect(ev.streamerNo(), streamId);
    }

    @Async("aiTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStreamEnded(StreamEndedEvent ev) {
        if (collector.isActive(ev.streamerNo())) {
            collector.disconnect(ev.streamerNo());
        }
    }
}
