package com.najacks.backend.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "streamer_applications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class StreamerApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 호환용 단일 스크린샷 URL — 여러 장이 있을 경우 첫 번째와 동일.
     * 기존 데이터/관리자 도구가 단일 필드를 참조할 수 있어 유지함.
     */
    private String screenshotUrl;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "streamer_application_screenshots",
            joinColumns = @JoinColumn(name = "application_id")
    )
    @Column(name = "screenshot_url", nullable = false, length = 1024)
    @Builder.Default
    private List<String> screenshotUrls = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    private LocalDateTime reviewedAt;

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
