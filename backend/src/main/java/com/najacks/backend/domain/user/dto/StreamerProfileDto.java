package com.najacks.backend.domain.user.dto;

import com.najacks.backend.domain.user.entity.StreamerProfile;

public record StreamerProfileDto(
        Long id,
        Long userId,
        String nickname,
        String profileImage,
        String coverImage,
        String avatar,
        String bio,
        String broadcastSchedule,
        String youtubeUrl,
        String chzzkUrl,
        String soopUrl,
        String category,
        String scheduleImageUrl,
        Boolean verified,
        Long contentCount,
        Long clipCount
) {
    public static StreamerProfileDto from(StreamerProfile profile) {
        return from(profile, 0L, 0L);
    }

    public static StreamerProfileDto from(StreamerProfile profile, long contentCount, long clipCount) {
        return new StreamerProfileDto(
                profile.getId(),
                profile.getUser().getId(),
                profile.getUser().getNickname(),
                profile.getUser().getProfileImage(),
                profile.getCoverImage(),
                profile.getAvatar(),
                profile.getBio(),
                profile.getBroadcastSchedule(),
                profile.getYoutubeUrl(),
                profile.getChzzkUrl(),
                profile.getSoopUrl(),
                profile.getCategory(),
                profile.getScheduleImageUrl(),
                profile.getVerified(),
                contentCount,
                clipCount
        );
    }
}
