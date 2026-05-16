package com.najacks.backend.domain.user.dto;

public record StreamerProfileUpdateRequest(
        String coverImage,
        String avatar,
        String bio,
        String broadcastSchedule,
        String youtubeUrl,
        String chzzkUrl,
        String soopUrl,
        String scheduleImageUrl,
        String category
) {
}
