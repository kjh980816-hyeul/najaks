package com.najacks.backend.domain.content.dto;

import com.najacks.backend.domain.content.entity.ContentCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ContentCreateRequest(
        @NotBlank(message = "제목은 필수입니다")
        String title,

        String description,

        String thumbnailUrl,

        List<String> imageUrls,

        String applyLink,

        String startDate,

        String endDate,

        @NotNull(message = "카테고리는 필수입니다")
        ContentCategory category,

        List<ContentCategory> tags,

        String requirements,

        String prize,

        String recruitCount,

        Integer followerCount,

        Boolean followerUnlimited,

        String contactMethod,

        String contactInfo,

        String hostName
) {
}
