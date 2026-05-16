package com.najacks.backend.domain.chat.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "chat_minute_stats",
        indexes = @Index(name = "idx_stream_bucket", columnList = "stream_id,minute_bucket")
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ChatMinuteStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "streamer_no", nullable = false)
    private Long streamerNo;

    @Column(name = "stream_id", length = 64, nullable = false)
    private String streamId;

    @Column(name = "minute_bucket", nullable = false)
    private LocalDateTime minuteBucket;

    @Column(name = "chat_count", nullable = false)
    private Integer chatCount;

    @Column(name = "unique_chatters", nullable = false)
    private Integer uniqueChatters;

    @Column(name = "donation_count")
    @Builder.Default
    private Integer donationCount = 0;

    @Column(name = "donation_total_amount")
    @Builder.Default
    private Integer donationTotalAmount = 0;

    @Column(name = "sample_messages", columnDefinition = "TEXT")
    private String sampleMessages;
}
