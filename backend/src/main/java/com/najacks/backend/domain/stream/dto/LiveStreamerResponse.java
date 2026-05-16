package com.najacks.backend.domain.stream.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class LiveStreamerResponse {
    private final Long streamerId;
    private final String nickname;
    private final String profileImage;
    private final String chzzkUrl;
    private final String liveTitle;
    private final String liveCategory;
    private final Integer viewerCount;
    private final LocalDateTime startedAt;
}
