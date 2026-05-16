package com.najacks.backend.domain.stream.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "streamer_live_states",
        indexes = {
                @Index(name = "idx_live_status", columnList = "current_live_status"),
                @Index(name = "idx_featured", columnList = "is_featured,display_order")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class StreamerLiveState {

    /** User.id 와 동일 (1:1). 스트리머의 no로 사용. */
    @Id
    @Column(name = "streamer_no")
    private Long streamerNo;

    @Column(name = "platform", length = 20, nullable = false)
    @Builder.Default
    private String platform = "CHZZK";

    @Column(name = "chzzk_channel_id", length = 64)
    private String chzzkChannelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_live_status", length = 20, nullable = false)
    @Builder.Default
    private LiveStatus currentLiveStatus = LiveStatus.OFFLINE;

    @Column(name = "current_stream_id", length = 64)
    private String currentStreamId;

    @Column(name = "current_stream_title", length = 255)
    private String currentStreamTitle;

    @Column(name = "current_stream_category", length = 100)
    private String currentStreamCategory;

    @Column(name = "current_viewer_count")
    private Integer currentViewerCount;

    @Column(name = "current_stream_started_at")
    private LocalDateTime currentStreamStartedAt;

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    @Column(name = "last_live_at")
    private LocalDateTime lastLiveAt;

    @Column(name = "failure_count", nullable = false)
    @Builder.Default
    private Integer failureCount = 0;

    @Column(name = "offline_miss_count", nullable = false)
    @Builder.Default
    private Integer offlineMissCount = 0;

    @Column(name = "current_peak_viewer_count")
    private Integer currentPeakViewerCount;

    @Column(name = "notion_page_id", length = 50)
    private String notionPageId;

    @Column(name = "is_featured", nullable = false)
    @Builder.Default
    private Boolean featured = false;

    @Column(name = "display_order")
    private Integer displayOrder;
}
