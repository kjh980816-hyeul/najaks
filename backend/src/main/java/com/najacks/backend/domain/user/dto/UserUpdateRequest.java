package com.najacks.backend.domain.user.dto;

import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하입니다")
        String nickname,
        String profileImage
) {
}
