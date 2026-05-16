package com.najacks.backend.domain.post.dto;

import com.najacks.backend.domain.post.entity.Comment;

import java.time.LocalDateTime;

public record CommentResponse(
        Long id,
        Long postId,
        Long authorId,
        String authorNickname,
        String authorProfileImage,
        String content,
        LocalDateTime createdAt
) {
    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getPost().getId(),
                comment.getAuthor().getId(),
                comment.getAuthor().getNickname(),
                comment.getAuthor().getProfileImage(),
                comment.getContent(),
                comment.getCreatedAt()
        );
    }
}
