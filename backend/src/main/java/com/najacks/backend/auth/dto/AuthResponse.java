package com.najacks.backend.auth.dto;

import com.najacks.backend.domain.user.entity.Role;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        UserInfo user
) {
    public record UserInfo(
            Long id,
            String email,
            String nickname,
            String profileImage,
            Role role
    ) {
    }
}
