package com.najacks.backend.domain.post.controller;

import com.najacks.backend.domain.post.dto.*;
import com.najacks.backend.domain.post.entity.PostCategory;
import com.najacks.backend.domain.post.service.PostService;
import com.najacks.backend.global.response.ApiResponse;
import com.najacks.backend.global.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    // ── 공개: 인기글 ──

    @GetMapping("/api/public/posts/popular")
    public ResponseEntity<ApiResponse<List<PostResponse>>> getPopularPosts() {
        List<PostResponse> posts = postService.getPopularPosts();
        return ResponseEntity.ok(ApiResponse.success(posts));
    }

    // ── 공개: 스트리머 개인 게시판 ──

    @GetMapping("/api/public/streamers/{streamerId}/posts")
    public ResponseEntity<ApiResponse<Page<PostResponse>>> getStreamerBoardPosts(
            @PathVariable Long streamerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PostResponse> posts = postService.getStreamerBoardPosts(streamerId, userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(posts));
    }

    // ── 게시글 목록 (인증 필요) ──

    @GetMapping("/api/posts")
    public ResponseEntity<ApiResponse<Page<PostResponse>>> getPosts(
            @RequestParam(required = false) PostCategory category,
            @RequestParam(required = false) List<PostCategory> categories,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = SecurityUtil.getCurrentUserId();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PostResponse> posts;
        if (categories != null && !categories.isEmpty()) {
            posts = postService.getPostsByCategories(categories, userId, pageable);
        } else if (category != null) {
            posts = postService.getPosts(category, userId, pageable);
        } else {
            posts = postService.getAllPosts(userId, pageable);
        }
        return ResponseEntity.ok(ApiResponse.success(posts));
    }

    // ── 크리에이터 게시판 ──

    @GetMapping("/api/posts/creator")
    public ResponseEntity<ApiResponse<Page<PostResponse>>> getCreatorPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        Long userId = SecurityUtil.getCurrentUserId();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PostResponse> posts = postService.getCreatorPosts(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(posts));
    }

    @GetMapping("/api/posts/{id}")
    public ResponseEntity<ApiResponse<PostResponse>> getPost(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        PostResponse post = postService.getPost(id, userId);
        return ResponseEntity.ok(ApiResponse.success(post));
    }

    @PostMapping("/api/posts")
    public ResponseEntity<ApiResponse<PostResponse>> createPost(
            @Valid @RequestBody PostCreateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        PostResponse post = postService.createPost(userId, request);
        return ResponseEntity.ok(ApiResponse.success("게시글이 작성되었습니다", post));
    }

    @PutMapping("/api/posts/{id}")
    public ResponseEntity<ApiResponse<PostResponse>> updatePost(
            @PathVariable Long id,
            @Valid @RequestBody PostCreateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        PostResponse post = postService.updatePost(id, userId, request);
        return ResponseEntity.ok(ApiResponse.success("게시글이 수정되었습니다", post));
    }

    @DeleteMapping("/api/posts/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePost(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        postService.deletePost(id, userId);
        return ResponseEntity.ok(ApiResponse.success("게시글이 삭제되었습니다", null));
    }

    // ── 좋아요 ──

    @PostMapping("/api/posts/{id}/like")
    public ResponseEntity<ApiResponse<PostResponse>> toggleLike(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        PostResponse post = postService.toggleLike(id, userId);
        return ResponseEntity.ok(ApiResponse.success(post));
    }

    // ── 댓글 ──

    @GetMapping("/api/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<List<CommentResponse>>> getComments(@PathVariable Long postId) {
        List<CommentResponse> comments = postService.getComments(postId);
        return ResponseEntity.ok(ApiResponse.success(comments));
    }

    @PostMapping("/api/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(
            @PathVariable Long postId,
            @Valid @RequestBody CommentCreateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        CommentResponse comment = postService.createComment(postId, userId, request);
        return ResponseEntity.ok(ApiResponse.success("댓글이 작성되었습니다", comment));
    }

    @DeleteMapping("/api/comments/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        postService.deleteComment(id, userId);
        return ResponseEntity.ok(ApiResponse.success("댓글이 삭제되었습니다", null));
    }
}
