package com.najacks.backend.domain.clip.dto;

import com.najacks.backend.domain.clip.entity.Clip;

import java.time.LocalDateTime;

public record ClipResponse(
        Long id,
        Long streamerId,
        String streamerNickname,
        String title,
        String url,
        String thumbnailUrl,
        Integer viewCount,
        LocalDateTime createdAt
) {
    public static ClipResponse from(Clip clip) {
        return new ClipResponse(
                clip.getId(),
                clip.getStreamer().getId(),
                clip.getStreamer().getNickname(),
                clip.getTitle(),
                clip.getUrl(),
                clip.getThumbnailUrl(),
                clip.getViewCount(),
                clip.getCreatedAt()
        );
    }
}
