package com.najacks.backend.domain.report.ai;

import com.najacks.backend.domain.report.entity.AiStatus;
import com.najacks.backend.domain.report.entity.Report;
import com.najacks.backend.domain.report.event.ReportSubmittedEvent;
import com.najacks.backend.domain.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReportAiRetryScheduler {

    private static final int MAX_RETRY = 3;

    private final ReportRepository reportRepo;
    private final ApplicationEventPublisher publisher;

    /** 5분마다 FAILED 상태를 재시도 (최대 3회) */
    @Scheduled(fixedDelayString = "300000", initialDelay = 60000)
    @Transactional
    public void retryFailed() {
        List<Report> failed = reportRepo.findTop50ByAiStatusAndAiRetryCountLessThanOrderByIdAsc(
                AiStatus.FAILED, MAX_RETRY);
        if (failed.isEmpty()) return;
        log.info("AI 재시도 대상 {}건", failed.size());
        for (Report r : failed) {
            r.setAiStatus(AiStatus.PENDING);
            reportRepo.save(r);
            publisher.publishEvent(new ReportSubmittedEvent(r.getId()));
        }
    }
}
