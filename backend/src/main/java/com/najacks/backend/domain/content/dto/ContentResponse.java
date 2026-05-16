package com.najacks.backend.domain.content.dto;

import com.najacks.backend.domain.content.entity.Content;
import com.najacks.backend.domain.content.entity.ContentCategory;
import com.najacks.backend.domain.content.entity.ContentStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ContentResponse(
        Long id,
        Long streamerId,
        String streamerNickname,
        String streamerProfileImage,
        String title,
        String description,
        String thumbnailUrl,
        List<String> imageUrls,
        String applyLink,
        LocalDateTime startDate,
        LocalDateTime endDate,
        ContentCategory category,
        List<ContentCategory> tags,
        String requirements,
        String prize,
        String recruitCount,
        Integer followerCount,
        Boolean followerUnlimited,
        String contactMethod,
        String contactInfo,
        String hostName,
        ContentStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ContentResponse from(Content content) {
        return new ContentResponse(
                content.getId(),
                content.getStreamer().getId(),
                content.getStreamer().getNickname(),
                content.getStreamer().getProfileImage(),
                content.getTitle(),
                content.getDescription(),
                content.getThumbnailUrl(),
                content.getImageUrls(),
                content.getApplyLink(),
                content.getStartDate(),
                content.getEndDate(),
                content.getCategory(),
                content.getTags(),
                content.getRequirements(),
                content.getPrize(),
                content.getRecruitCount(),
                content.getFollowerCount(),
                content.getFollowerUnlimited(),
                content.getContactMethod(),
                content.getContactInfo(),
                content.getHostName(),
                content.getStatus(),
                content.getCreatedAt(),
                content.getUpdatedAt()
        );
    }
}
