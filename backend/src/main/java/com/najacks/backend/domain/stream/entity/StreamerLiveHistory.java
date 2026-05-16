package com.najacks.backend.domain.stream.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "streamer_live_histories",
        indexes = {
                @Index(name = "idx_streamer_started", columnList = "streamer_no,started_at"),
                @Index(name = "idx_started_at", columnList = "started_at")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class StreamerLiveHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "streamer_no", nullable = false)
    private Long streamerNo;

    @Column(name = "platform", length = 20, nullable = false)
    @Builder.Default
    private String platform = "CHZZK";

    @Column(name = "stream_id", length = 64)
    private String streamId;

    @Column(length = 255)
    private String title;

    @Column(length = 100)
    private String category;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "peak_viewer_count")
    private Integer peakViewerCount;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;
}
