package com.najacks.backend.domain.post.repository;

import com.najacks.backend.domain.post.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByPostId(Long postId);

    List<Comment> findByPostIdAndHiddenFalse(Long postId);

    int countByPostIdAndHiddenFalse(Long postId);

    void deleteAllByPostId(Long postId);
}
