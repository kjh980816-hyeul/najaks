package com.najacks.backend.domain.chat.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "stream_analysis_reports",
        uniqueConstraints = @UniqueConstraint(name = "uk_stream_id", columnNames = "stream_id"),
        indexes = @Index(name = "idx_streamer_created", columnList = "streamer_no,created_at")
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class StreamAnalysisReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "streamer_no", nullable = false)
    private Long streamerNo;

    @Column(name = "stream_id", length = 64, nullable = false)
    private String streamId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at", nullable = false)
    private LocalDateTime endedAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "total_chat_count")
    private Integer totalChatCount;

    @Column(name = "unique_chatter_count")
    private Integer uniqueChatterCount;

    @Column(name = "peak_viewer_count")
    private Integer peakViewerCount;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "ai_mood", length = 100)
    private String aiMood;

    @Column(name = "ai_top_keywords", length = 500)
    private String aiTopKeywords;

    @Column(name = "ai_audience_insight", columnDefinition = "TEXT")
    private String aiAudienceInsight;

    @Column(name = "ai_highlight_moments", columnDefinition = "TEXT")
    private String aiHighlightMoments;

    @Column(name = "ai_improvement_tips", columnDefinition = "TEXT")
    private String aiImprovementTips;

    @Column(name = "highlight_count")
    @Builder.Default
    private Integer highlightCount = 0;

    @Column(name = "notion_page_id", length = 50)
    private String notionPageId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
