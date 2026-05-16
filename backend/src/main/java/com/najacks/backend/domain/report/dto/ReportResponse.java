package com.najacks.backend.domain.report.dto;

import com.najacks.backend.domain.report.entity.Report;
import com.najacks.backend.domain.report.entity.ReportStatus;
import com.najacks.backend.domain.report.entity.ReportTargetType;

import java.time.LocalDateTime;

public record ReportResponse(
        Long id,
        Long reporterId,
        String reporterNickname,
        ReportTargetType targetType,
        Long targetId,
        String targetPreview,
        String reason,
        ReportStatus status,
        String processedByNickname,
        LocalDateTime processedAt,
        LocalDateTime createdAt
) {
    public static ReportResponse of(Report report, String targetPreview) {
        return new ReportResponse(
                report.getId(),
                report.getReporter().getId(),
                report.getReporter().getNickname(),
                report.getTargetType(),
                report.getTargetId(),
                targetPreview,
                report.getReason(),
                report.getStatus(),
                report.getProcessedBy() != null ? report.getProcessedBy().getNickname() : null,
                report.getProcessedAt(),
                report.getCreatedAt()
        );
    }
}
