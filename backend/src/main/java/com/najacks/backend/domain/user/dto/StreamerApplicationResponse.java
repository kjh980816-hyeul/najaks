package com.najacks.backend.domain.user.dto;

import com.najacks.backend.domain.user.entity.ApplicationStatus;
import com.najacks.backend.domain.user.entity.StreamerApplication;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public record StreamerApplicationResponse(
        Long id,
        Long userId,
        String userEmail,
        String userNickname,
        String screenshotUrl,
        List<String> screenshotUrls,
        ApplicationStatus status,
        String reviewedByNickname,
        LocalDateTime reviewedAt,
        String rejectionReason,
        LocalDateTime createdAt
) {
    public static StreamerApplicationResponse from(StreamerApplication app) {
        List<String> urls = app.getScreenshotUrls();
        if (urls == null || urls.isEmpty()) {
            urls = app.getScreenshotUrl() != null ? List.of(app.getScreenshotUrl()) : new ArrayList<>();
        }
        String primary = !urls.isEmpty() ? urls.get(0) : app.getScreenshotUrl();
        return new StreamerApplicationResponse(
                app.getId(),
                app.getUser().getId(),
                app.getUser().getEmail(),
                app.getUser().getNickname(),
                primary,
                urls,
                app.getStatus(),
                app.getReviewedBy() != null ? app.getReviewedBy().getNickname() : null,
                app.getReviewedAt(),
                app.getRejectionReason(),
                app.getCreatedAt()
        );
    }
}
