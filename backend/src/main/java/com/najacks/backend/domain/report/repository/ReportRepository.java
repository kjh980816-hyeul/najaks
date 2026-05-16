package com.najacks.backend.domain.report.repository;

import com.najacks.backend.domain.report.entity.AiStatus;
import com.najacks.backend.domain.report.entity.Report;
import com.najacks.backend.domain.report.entity.ReportStatus;
import com.najacks.backend.domain.report.entity.ReportTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByStatus(ReportStatus status);

    List<Report> findAllByOrderByCreatedAtDesc();

    List<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status);

    long countByTargetTypeAndTargetId(ReportTargetType targetType, Long targetId);

    boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, ReportTargetType targetType, Long targetId);

    List<Report> findTop50ByAiStatusAndAiRetryCountLessThanOrderByIdAsc(AiStatus status, int maxRetry);
}
