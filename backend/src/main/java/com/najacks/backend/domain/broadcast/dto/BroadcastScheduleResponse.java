package com.najacks.backend.domain.broadcast.dto;

import com.najacks.backend.domain.broadcast.entity.BroadcastSchedule;

import java.time.LocalDateTime;

public record BroadcastScheduleResponse(
        Long id,
        Long streamerId,
        String streamerNickname,
        String title,
        String description,
        String imageUrl,
        LocalDateTime scheduledAt,
        LocalDateTime createdAt
) {
    public static BroadcastScheduleResponse from(BroadcastSchedule schedule) {
        return new BroadcastScheduleResponse(
                schedule.getId(),
                schedule.getStreamer().getId(),
                schedule.getStreamer().getNickname(),
                schedule.getTitle(),
                schedule.getDescription(),
                schedule.getImageUrl(),
                schedule.getScheduledAt(),
                schedule.getCreatedAt()
        );
    }
}
