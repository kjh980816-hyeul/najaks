package com.najacks.backend.domain.chat.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "chat_highlights",
        indexes = @Index(name = "idx_stream_time", columnList = "stream_id,occurred_at")
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ChatHighlight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "streamer_no", nullable = false)
    private Long streamerNo;

    @Column(name = "stream_id", length = 64, nullable = false)
    private String streamId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "stream_offset_sec")
    private Integer streamOffsetSec;

    @Column(name = "cps_observed")
    private Float cpsObserved;

    @Column(name = "cps_baseline")
    private Float cpsBaseline;

    @Column(name = "magnitude")
    private Float magnitude;

    @Column(name = "ai_summary", length = 500)
    private String aiSummary;

    @Column(name = "sample_messages", columnDefinition = "TEXT")
    private String sampleMessages;
}
