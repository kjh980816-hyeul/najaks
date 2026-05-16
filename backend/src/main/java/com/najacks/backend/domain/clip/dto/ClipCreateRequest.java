package com.najacks.backend.domain.clip.dto;

import jakarta.validation.constraints.NotBlank;

public record ClipCreateRequest(
        @NotBlank(message = "제목은 필수입니다")
        String title,

        @NotBlank(message = "URL은 필수입니다")
        String url,

        String thumbnailUrl
) {
}
