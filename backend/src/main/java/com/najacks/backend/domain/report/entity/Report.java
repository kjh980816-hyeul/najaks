package com.najacks.backend.domain.report.entity;

import com.najacks.backend.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "reports",
        indexes = {
                @Index(name = "idx_ai_status_retry", columnList = "ai_status,ai_retry_count"),
                @Index(name = "idx_status_ai_severity", columnList = "status,ai_severity")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportTargetType targetType;

    @Column(nullable = false)
    private Long targetId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReportStatus status = ReportStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by")
    private User processedBy;

    private LocalDateTime processedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /* ---- AI 분류 필드 ---- */
    @Enumerated(EnumType.STRING)
    @Column(name = "ai_status", length = 20)
    @Builder.Default
    private AiStatus aiStatus = AiStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_category", length = 30)
    private ReportCategory aiCategory;

    @Column(name = "ai_severity")
    private Integer aiSeverity;

    @Column(name = "ai_summary", length = 500)
    private String aiSummary;

    @Column(name = "ai_keywords", length = 300)
    private String aiKeywords;

    @Column(name = "ai_retry_count", nullable = false)
    @Builder.Default
    private Integer aiRetryCount = 0;

    @Column(name = "ai_fail_reason", length = 500)
    private String aiFailReason;

    @Column(name = "ai_processed_at")
    private LocalDateTime aiProcessedAt;

    @Column(name = "notion_page_id", length = 50)
    private String notionPageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "admin_action", length = 30)
    private AdminAction adminAction;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;
}
