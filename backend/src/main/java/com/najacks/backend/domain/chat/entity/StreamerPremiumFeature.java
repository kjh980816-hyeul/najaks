package com.najacks.backend.domain.chat.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "streamer_premium_features",
        indexes = @Index(name = "idx_enabled", columnList = "chat_analysis_enabled")
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class StreamerPremiumFeature {

    @Id
    @Column(name = "streamer_no")
    private Long streamerNo;

    @Column(name = "chat_analysis_enabled", nullable = false)
    @Builder.Default
    private Boolean chatAnalysisEnabled = false;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "payment_ref", length = 100)
    private String paymentRef;

    @Column(name = "chzzk_access_token_enc", length = 1024)
    private String chzzkAccessTokenEnc;

    @Column(name = "chzzk_refresh_token_enc", length = 1024)
    private String chzzkRefreshTokenEnc;

    @Column(name = "chzzk_token_expires_at")
    private LocalDateTime chzzkTokenExpiresAt;

    @Column(name = "chzzk_scope", length = 200)
    private String chzzkScope;

    @Column(name = "chzzk_channel_id", length = 64)
    private String chzzkChannelId;

    @Column(name = "chzzk_channel_name", length = 100)
    private String chzzkChannelName;

    @Column(name = "report_email_enc", length = 1024)
    private String reportEmailEnc;

    @Column(name = "email_enabled", nullable = false)
    @Builder.Default
    private Boolean emailEnabled = true;

    @Column(name = "email_failure_count", nullable = false)
    @Builder.Default
    private Integer emailFailureCount = 0;

    @Column(name = "last_report_sent_at")
    private LocalDateTime lastReportSentAt;

    @Column(name = "last_email_error", length = 500)
    private String lastEmailError;

    public void resetEmailFailure() { this.emailFailureCount = 0; this.lastEmailError = null; }
    public void incrementEmailFailure() { this.emailFailureCount = (this.emailFailureCount == null ? 0 : this.emailFailureCount) + 1; }
}
