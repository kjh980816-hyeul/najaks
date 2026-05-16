package com.najacks.backend.domain.report.ai;

import com.najacks.backend.domain.report.dto.AiClassificationResult;
import com.najacks.backend.domain.report.entity.AiStatus;
import com.najacks.backend.domain.report.entity.Report;
import com.najacks.backend.domain.report.entity.ReportCategory;
import com.najacks.backend.domain.report.event.ReportSubmittedEvent;
import com.najacks.backend.domain.report.notion.ReportNotionSyncService;
import com.najacks.backend.domain.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReportAiProcessor {

    private final ReportRepository reportRepo;
    private final GeminiReportClassifier classifier;
    private final ReportNotionSyncService notionSync;

    @Async("aiTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(ReportSubmittedEvent event) {
        Report report = reportRepo.findById(event.reportId()).orElse(null);
        if (report == null) {
            log.warn("Report not found for AI processing: id={}", event.reportId());
            return;
        }

        runStep("AI 분류", report, () -> {
            report.setAiStatus(AiStatus.PROCESSING);
            reportRepo.save(report);

            AiClassificationResult r = classifier.classify(report.getReason(), report.getId());
            applyResult(report, r);
            report.setAiStatus(AiStatus.DONE);
            report.setAiProcessedAt(LocalDateTime.now());
            reportRepo.save(report);
        }, () -> {
            report.setAiStatus(AiStatus.FAILED);
            report.setAiRetryCount(report.getAiRetryCount() + 1);
            reportRepo.save(report);
        });

        if (report.getAiStatus() == AiStatus.DONE) {
            runStep("Notion 생성", report, () -> {
                if (report.getNotionPageId() != null) return;
                String pageId = notionSync.createReportPage(report);
                if (pageId != null) {
                    report.setNotionPageId(pageId);
                    reportRepo.save(report);
                }
            }, null);
        }
    }

    private void applyResult(Report report, AiClassificationResult r) {
        if (r == null) return;
        try {
            if (r.getCategory() != null) {
                report.setAiCategory(ReportCategory.valueOf(r.getCategory()));
            }
        } catch (IllegalArgumentException ignored) {
            report.setAiCategory(ReportCategory.ETC);
        }
        report.setAiSeverity(r.getSeverity());
        report.setAiSummary(truncate(r.getSummary(), 500));
        if (r.getKeywords() != null && !r.getKeywords().isEmpty()) {
            String joined = r.getKeywords().stream()
                    .filter(s -> s != null && !s.isBlank())
                    .limit(5)
                    .collect(Collectors.joining(","));
            report.setAiKeywords(truncate(joined, 300));
        }
        report.setAiFailReason(null);
    }

    private void runStep(String name, Report report, Runnable task, Runnable onFail) {
        try {
            task.run();
        } catch (Exception e) {
            log.error("[{}] 실패 reportId={}", name, report.getId(), e);
            report.setAiFailReason(truncate(name + ": " + safeMessage(e), 500));
            if (onFail != null) {
                try { onFail.run(); } catch (Exception ignored) {}
            }
        }
    }

    private String truncate(String s, int limit) {
        if (s == null) return null;
        return s.length() <= limit ? s : s.substring(0, limit);
    }

    private String safeMessage(Exception e) {
        String m = e.getMessage();
        return m != null ? m : e.getClass().getSimpleName();
    }
}
