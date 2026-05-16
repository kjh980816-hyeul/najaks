package com.najacks.backend.domain.broadcast.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record BroadcastScheduleRequest(
        @NotBlank(message = "제목은 필수입니다")
        String title,

        String description,

        @NotNull(message = "방송 예정 시간은 필수입니다")
        LocalDateTime scheduledAt
) {
}
