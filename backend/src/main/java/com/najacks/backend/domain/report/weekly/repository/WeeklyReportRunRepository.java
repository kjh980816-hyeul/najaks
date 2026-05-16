package com.najacks.backend.domain.report.weekly.repository;

import com.najacks.backend.domain.report.weekly.entity.WeeklyReportRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WeeklyReportRunRepository extends JpaRepository<WeeklyReportRun, Long> {
    boolean existsByWeekLabelAndStatus(String weekLabel, String status);
    Optional<WeeklyReportRun> findByWeekLabel(String weekLabel);
}
