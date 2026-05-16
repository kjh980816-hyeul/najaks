package com.najacks.backend.domain.post.repository;

import com.najacks.backend.domain.post.entity.Post;
import com.najacks.backend.domain.post.entity.PostCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    // ── 메인 커뮤니티 피드 (스트리머 개인 게시판 글 제외) ──
    Page<Post> findByCategoryAndHiddenFalseAndTargetStreamerIsNull(PostCategory category, Pageable pageable);

    Page<Post> findByCategoryInAndHiddenFalseAndTargetStreamerIsNull(List<PostCategory> categories, Pageable pageable);

    Page<Post> findByHiddenFalseAndTargetStreamerIsNull(Pageable pageable);

    // ── 스트리머 개인 게시판 ──
    Page<Post> findByTargetStreamerIdAndHiddenFalse(Long streamerId, Pageable pageable);

    // ── 기타 ──
    List<Post> findByAuthorId(Long authorId);

    // 레거시: 내부 로직에서 쓰던 것 — 유지
    Page<Post> findByCategoryAndHiddenFalse(PostCategory category, Pageable pageable);
    Page<Post> findByCategoryInAndHiddenFalse(List<PostCategory> categories, Pageable pageable);
    Page<Post> findByHiddenFalse(Pageable pageable);
}
