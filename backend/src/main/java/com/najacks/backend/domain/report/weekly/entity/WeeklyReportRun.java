package com.najacks.backend.domain.report.weekly.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "weekly_report_runs",
        uniqueConstraints = @UniqueConstraint(name = "uk_week", columnNames = "week_label")
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class WeeklyReportRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "week_label", nullable = false, length = 20)
    private String weekLabel;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "week_end", nullable = false)
    private LocalDate weekEnd;

    @Column(name = "notion_page_id", length = 50)
    private String notionPageId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    public static WeeklyReportRun start(String weekLabel, LocalDate start, LocalDate end) {
        return WeeklyReportRun.builder()
                .weekLabel(weekLabel)
                .weekStart(start)
                .weekEnd(end)
                .status("RUNNING")
                .startedAt(LocalDateTime.now())
                .build();
    }

    public void markSuccess(String pageId) {
        this.notionPageId = pageId;
        this.status = "SUCCESS";
        this.finishedAt = LocalDateTime.now();
    }

    public void markFailed(String message) {
        this.status = "FAILED";
        this.errorMessage = message != null && message.length() > 500
                ? message.substring(0, 500) : message;
        this.finishedAt = LocalDateTime.now();
    }
}
