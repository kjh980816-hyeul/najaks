package com.najacks.backend.domain.chat.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "email_report_logs",
        indexes = @Index(name = "idx_streamer_sent", columnList = "streamer_no,sent_at")
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EmailReportLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "streamer_no", nullable = false)
    private Long streamerNo;

    @Column(name = "analysis_report_id", nullable = false)
    private Long analysisReportId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "subject", length = 200)
    private String subject;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    public static EmailReportLog success(Long streamerNo, Long reportId, String subject) {
        return EmailReportLog.builder()
                .streamerNo(streamerNo)
                .analysisReportId(reportId)
                .status("SENT")
                .subject(subject)
                .sentAt(LocalDateTime.now())
                .build();
    }

    public static EmailReportLog failure(Long streamerNo, Long reportId, String error) {
        return EmailReportLog.builder()
                .streamerNo(streamerNo)
                .analysisReportId(reportId)
                .status("FAILED")
                .errorMessage(error != null && error.length() > 500 ? error.substring(0, 500) : error)
                .sentAt(LocalDateTime.now())
                .build();
    }
}
