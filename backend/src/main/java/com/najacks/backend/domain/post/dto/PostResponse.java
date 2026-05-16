package com.najacks.backend.domain.post.dto;

import com.najacks.backend.domain.post.entity.Post;
import com.najacks.backend.domain.post.entity.PostCategory;

import java.time.LocalDateTime;

public record PostResponse(
        Long id,
        Long authorId,
        String authorNickname,
        String authorProfileImage,
        String title,
        String content,
        String imageUrl,
        PostCategory category,
        Integer viewCount,
        Integer likeCount,
        Integer commentCount,
        Boolean liked,
        Long targetStreamerId,
        String targetStreamerNickname,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PostResponse from(Post post, int commentCount, boolean liked) {
        return new PostResponse(
                post.getId(),
                post.getAuthor().getId(),
                post.getAuthor().getNickname(),
                post.getAuthor().getProfileImage(),
                post.getTitle(),
                post.getContent(),
                post.getImageUrl(),
                post.getCategory(),
                post.getViewCount(),
                post.getLikeCount(),
                commentCount,
                liked,
                post.getTargetStreamer() != null ? post.getTargetStreamer().getId() : null,
                post.getTargetStreamer() != null ? post.getTargetStreamer().getNickname() : null,
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }

    public static PostResponse from(Post post, int commentCount) {
        return from(post, commentCount, false);
    }
}
