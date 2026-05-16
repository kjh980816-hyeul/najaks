package com.najacks.backend.domain.user.dto;

import com.najacks.backend.domain.user.entity.Role;
import com.najacks.backend.domain.user.entity.User;

public record UserProfileResponse(
        Long id,
        String email,
        String nickname,
        String profileImage,
        Role role,
        StreamerProfileDto streamerProfile
) {
    public static UserProfileResponse from(User user, StreamerProfileDto streamerProfile) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImage(),
                user.getRole(),
                streamerProfile
        );
    }
}
