package com.najacks.backend.domain.user.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record StreamerApplicationRequest(
        @NotEmpty(message = "스크린샷이 1장 이상 필요합니다")
        List<String> screenshotUrls
) {
}
