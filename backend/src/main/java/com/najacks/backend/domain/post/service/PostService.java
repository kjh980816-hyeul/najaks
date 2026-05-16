package com.najacks.backend.domain.post.service;

import com.najacks.backend.domain.post.dto.*;
import com.najacks.backend.domain.post.entity.*;
import com.najacks.backend.domain.post.repository.CommentRepository;
import com.najacks.backend.domain.post.repository.PostLikeRepository;
import com.najacks.backend.domain.post.repository.PostRepository;
import com.najacks.backend.domain.report.repository.BlockRepository;
import com.najacks.backend.domain.user.entity.Role;
import com.najacks.backend.domain.user.entity.User;
import com.najacks.backend.domain.user.repository.StreamerProfileRepository;
import com.najacks.backend.domain.user.repository.UserRepository;
import com.najacks.backend.global.exception.CustomException;
import com.najacks.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;
    private final BlockRepository blockRepository;
    private final UserRepository userRepository;
    private final StreamerProfileRepository streamerProfileRepository;

    private static final int REPORT_HIDE_THRESHOLD = 5;

    private java.util.Set<Long> getBlockedUserIds(Long userId) {
        if (userId == null) return java.util.Collections.emptySet();
        return blockRepository.findByBlockerId(userId).stream()
                .map(b -> b.getBlocked().getId())
                .collect(java.util.stream.Collectors.toSet());
    }

    private boolean isVerifiedStreamerOrAdmin(Long userId) {
        if (userId == null) return false;
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return false;
        if (user.getRole() == Role.ADMIN) return true;
        if (user.getRole() != Role.STREAMER) return false;
        return streamerProfileRepository.findByUserId(userId)
                .map(sp -> Boolean.TRUE.equals(sp.getVerified()))
                .orElse(false);
    }

    // ── 게시글 CRUD ──

    private Page<PostResponse> toResponsePage(Page<Post> posts, java.util.Set<Long> blockedIds, Long currentUserId, Pageable pageable) {
        List<PostResponse> list = posts.getContent().stream()
                .filter(post -> !blockedIds.contains(post.getAuthor().getId()))
                .map(post -> {
                    int cc = commentRepository.countByPostIdAndHiddenFalse(post.getId());
                    boolean liked = currentUserId != null && postLikeRepository.existsByPostIdAndUserId(post.getId(), currentUserId);
                    return PostResponse.from(post, cc, liked);
                })
                .toList();
        return new org.springframework.data.domain.PageImpl<>(list, pageable, posts.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getPosts(PostCategory category, Long currentUserId, Pageable pageable) {
        if (category == PostCategory.STREAMER_ONLY && !isVerifiedStreamerOrAdmin(currentUserId)) {
            throw new CustomException(ErrorCode.STREAMER_NOT_VERIFIED);
        }
        java.util.Set<Long> blockedIds = getBlockedUserIds(currentUserId);
        Page<Post> posts = postRepository.findByCategoryAndHiddenFalseAndTargetStreamerIsNull(category, pageable);
        return toResponsePage(posts, blockedIds, currentUserId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getAllPosts(Long currentUserId, Pageable pageable) {
        java.util.Set<Long> blockedIds = getBlockedUserIds(currentUserId);
        Page<Post> posts = postRepository.findByHiddenFalseAndTargetStreamerIsNull(pageable);
        return toResponsePage(posts, blockedIds, currentUserId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getPostsByCategories(List<PostCategory> categories, Long currentUserId, Pageable pageable) {
        if (categories.contains(PostCategory.STREAMER_ONLY) && !isVerifiedStreamerOrAdmin(currentUserId)) {
            categories = categories.stream()
                    .filter(c -> c != PostCategory.STREAMER_ONLY)
                    .toList();
            if (categories.isEmpty()) {
                throw new CustomException(ErrorCode.STREAMER_NOT_VERIFIED);
            }
        }
        java.util.Set<Long> blockedIds = getBlockedUserIds(currentUserId);
        Page<Post> posts = postRepository.findByCategoryInAndHiddenFalseAndTargetStreamerIsNull(categories, pageable);
        return toResponsePage(posts, blockedIds, currentUserId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getStreamerBoardPosts(Long streamerId, Long currentUserId, Pageable pageable) {
        java.util.Set<Long> blockedIds = getBlockedUserIds(currentUserId);
        Page<Post> posts = postRepository.findByTargetStreamerIdAndHiddenFalse(streamerId, pageable);
        return toResponsePage(posts, blockedIds, currentUserId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getCreatorPosts(Long currentUserId, Pageable pageable) {
        List<PostCategory> creatorCategories = List.of(
                PostCategory.PORTFOLIO,
                PostCategory.WORK_REVIEW,
                PostCategory.JOB_SEEKING,
                PostCategory.RECRUITMENT
        );
        Page<Post> posts = postRepository.findByCategoryInAndHiddenFalse(creatorCategories, pageable);
        return posts.map(post -> {
            int commentCount = commentRepository.countByPostIdAndHiddenFalse(post.getId());
            boolean liked = currentUserId != null && postLikeRepository.existsByPostIdAndUserId(post.getId(), currentUserId);
            return PostResponse.from(post, commentCount, liked);
        });
    }

    @Transactional
    public PostResponse getPost(Long postId, Long currentUserId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        // STREAMER_ONLY 게시글은 인증된 스트리머/관리자만 조회 가능
        if (post.getCategory() == PostCategory.STREAMER_ONLY && !isVerifiedStreamerOrAdmin(currentUserId)) {
            throw new CustomException(ErrorCode.STREAMER_NOT_VERIFIED);
        }

        post.increaseViewCount();

        int commentCount = commentRepository.countByPostIdAndHiddenFalse(postId);
        boolean liked = currentUserId != null && postLikeRepository.existsByPostIdAndUserId(postId, currentUserId);
        return PostResponse.from(post, commentCount, liked);
    }

    @Transactional
    public PostResponse createPost(Long userId, PostCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // NOTICE 카테고리는 관리자만
        if (request.category() == PostCategory.NOTICE && user.getRole() != Role.ADMIN) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        // STREAMER_ONLY 카테고리는 관리자 승인된 스트리머만
        if (request.category() == PostCategory.STREAMER_ONLY && user.getRole() != Role.ADMIN) {
            if (user.getRole() != Role.STREAMER) {
                throw new CustomException(ErrorCode.STREAMER_NOT_VERIFIED);
            }
            boolean verified = streamerProfileRepository.findByUserId(user.getId())
                    .map(sp -> Boolean.TRUE.equals(sp.getVerified()))
                    .orElse(false);
            if (!verified) {
                throw new CustomException(ErrorCode.STREAMER_NOT_VERIFIED);
            }
        }

        // RECRUITMENT 카테고리는 관리자 승인된 스트리머만
        if (request.category() == PostCategory.RECRUITMENT && user.getRole() != Role.ADMIN) {
            if (user.getRole() != Role.STREAMER) {
                throw new CustomException(ErrorCode.ACCESS_DENIED);
            }
            boolean verified = streamerProfileRepository.findByUserId(user.getId())
                    .map(sp -> Boolean.TRUE.equals(sp.getVerified()))
                    .orElse(false);
            if (!verified) {
                throw new CustomException(ErrorCode.STREAMER_NOT_VERIFIED);
            }
        }

        User targetStreamer = null;
        if (request.targetStreamerId() != null) {
            targetStreamer = userRepository.findById(request.targetStreamerId())
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        }

        Post post = Post.builder()
                .author(user)
                .title(request.title())
                .content(request.content())
                .imageUrl(request.imageUrl())
                .category(request.category())
                .targetStreamer(targetStreamer)
                .viewCount(0)
                .likeCount(0)
                .reportCount(0)
                .hidden(false)
                .build();

        Post saved = postRepository.save(post);
        return PostResponse.from(saved, 0, false);
    }

    @Transactional
    public PostResponse updatePost(Long postId, Long userId, PostCreateRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (!post.getAuthor().getId().equals(userId) && user.getRole() != Role.ADMIN) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        // NOTICE 카테고리는 관리자만
        if (request.category() == PostCategory.NOTICE && user.getRole() != Role.ADMIN) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        post.update(request.title(), request.content(), request.category(), request.imageUrl());

        int commentCount = commentRepository.countByPostIdAndHiddenFalse(postId);
        boolean liked = postLikeRepository.existsByPostIdAndUserId(postId, userId);
        return PostResponse.from(post, commentCount, liked);
    }

    @Transactional
    public void deletePost(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        boolean isAuthor = post.getAuthor().getId().equals(userId);
        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean isBoardOwner = post.getTargetStreamer() != null
                && post.getTargetStreamer().getId().equals(userId);
        if (!isAuthor && !isAdmin && !isBoardOwner) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        postLikeRepository.deleteAllByPostId(postId);
        commentRepository.deleteAllByPostId(postId);
        postRepository.delete(post);
    }

    // ── 좋아요 ──

    @Transactional
    public PostResponse toggleLike(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        var existing = postLikeRepository.findByPostIdAndUserId(postId, userId);
        int newLikeCount;
        boolean liked;

        if (existing.isPresent()) {
            postLikeRepository.delete(existing.get());
            newLikeCount = Math.max(0, post.getLikeCount() - 1);
            liked = false;
        } else {
            PostLike like = PostLike.builder().post(post).user(user).build();
            postLikeRepository.save(like);
            newLikeCount = post.getLikeCount() + 1;
            liked = true;
        }

        post.setLikeCountValue(newLikeCount);

        int commentCount = commentRepository.countByPostIdAndHiddenFalse(postId);
        return PostResponse.from(post, commentCount, liked);
    }

    // ── 댓글 ──

    @Transactional(readOnly = true)
    public List<CommentResponse> getComments(Long postId) {
        return commentRepository.findByPostIdAndHiddenFalse(postId).stream()
                .map(CommentResponse::from)
                .toList();
    }

    @Transactional
    public CommentResponse createComment(Long postId, Long userId, CommentCreateRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Comment comment = Comment.builder()
                .post(post)
                .author(user)
                .content(request.content())
                .reportCount(0)
                .hidden(false)
                .build();

        Comment saved = commentRepository.save(comment);
        return CommentResponse.from(saved);
    }

    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (!comment.getAuthor().getId().equals(userId) && user.getRole() != Role.ADMIN) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        commentRepository.delete(comment);
    }

    // ── 인기글 (메인 페이지용) ──

    @Transactional(readOnly = true)
    public List<PostResponse> getPopularPosts() {
        return postRepository.findAll().stream()
                .filter(p -> !p.getHidden() && p.getCategory() == PostCategory.FREE)
                .sorted((a, b) -> b.getLikeCount() - a.getLikeCount())
                .limit(5)
                .map(p -> PostResponse.from(p, commentRepository.countByPostIdAndHiddenFalse(p.getId())))
                .toList();
    }
}
