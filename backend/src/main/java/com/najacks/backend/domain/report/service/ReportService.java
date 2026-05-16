package com.najacks.backend.domain.report.service;

import com.najacks.backend.domain.notification.entity.NotificationType;
import com.najacks.backend.domain.notification.service.NotificationService;
import com.najacks.backend.domain.post.entity.Comment;
import com.najacks.backend.domain.post.entity.Post;
import com.najacks.backend.domain.post.repository.CommentRepository;
import com.najacks.backend.domain.post.repository.PostRepository;
import com.najacks.backend.domain.report.dto.ReportRequest;
import com.najacks.backend.domain.report.dto.ReportResponse;
import com.najacks.backend.domain.report.entity.*;
import com.najacks.backend.domain.report.event.ReportSubmittedEvent;
import com.najacks.backend.domain.report.repository.BlockRepository;
import com.najacks.backend.domain.report.repository.ReportRepository;
import com.najacks.backend.domain.user.entity.User;
import com.najacks.backend.domain.user.repository.UserRepository;
import com.najacks.backend.global.exception.CustomException;
import com.najacks.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final BlockRepository blockRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    private static final int REPORT_HIDE_THRESHOLD = 5;

    @Transactional
    public void createReport(Long userId, ReportRequest request) {
        User reporter = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 중복 신고 방지
        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(userId, request.targetType(), request.targetId())) {
            throw new CustomException(ErrorCode.ALREADY_REPORTED);
        }

        Report report = Report.builder()
                .reporter(reporter)
                .targetType(request.targetType())
                .targetId(request.targetId())
                .reason(request.reason())
                .status(ReportStatus.PENDING)
                .build();

        Report saved = reportRepository.save(report);

        // 신고 누적 확인 → 자동 숨김
        long count = reportRepository.countByTargetTypeAndTargetId(request.targetType(), request.targetId());
        if (count >= REPORT_HIDE_THRESHOLD) {
            autoHideTarget(request.targetType(), request.targetId());
        }

        // AFTER_COMMIT 리스너가 AI 분류·Notion 동기화 수행
        eventPublisher.publishEvent(new ReportSubmittedEvent(saved.getId()));

        log.info("Report created: type={}, targetId={}, reporter={}", request.targetType(), request.targetId(), userId);
    }

    private void autoHideTarget(ReportTargetType type, Long targetId) {
        if (type == ReportTargetType.POST) {
            postRepository.findById(targetId).ifPresent(post -> {
                Post hidden = Post.builder()
                        .id(post.getId()).author(post.getAuthor())
                        .title(post.getTitle()).content(post.getContent())
                        .category(post.getCategory()).viewCount(post.getViewCount())
                        .likeCount(post.getLikeCount()).reportCount(post.getReportCount())
                        .hidden(true).build();
                postRepository.save(hidden);
                log.info("Post auto-hidden: id={}", targetId);
            });
        } else if (type == ReportTargetType.COMMENT) {
            commentRepository.findById(targetId).ifPresent(comment -> {
                Comment hidden = Comment.builder()
                        .id(comment.getId()).post(comment.getPost())
                        .author(comment.getAuthor()).content(comment.getContent())
                        .reportCount(comment.getReportCount()).hidden(true).build();
                commentRepository.save(hidden);
                log.info("Comment auto-hidden: id={}", targetId);
            });
        }
    }

    // ── 차단 ──

    @Transactional
    public void blockUser(Long blockerId, Long blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new IllegalArgumentException("자기 자신을 차단할 수 없습니다");
        }

        User blocker = userRepository.findById(blockerId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        User blocked = userRepository.findById(blockedId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            throw new IllegalArgumentException("이미 차단한 사용자입니다");
        }

        Block block = Block.builder().blocker(blocker).blocked(blocked).build();
        blockRepository.save(block);
    }

    @Transactional
    public void unblockUser(Long blockerId, Long blockedId) {
        blockRepository.findAll().stream()
                .filter(b -> b.getBlocker().getId().equals(blockerId) && b.getBlocked().getId().equals(blockedId))
                .findFirst()
                .ifPresent(blockRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<Long> getBlockedUserIds(Long userId) {
        return blockRepository.findByBlockerId(userId).stream()
                .map(b -> b.getBlocked().getId())
                .toList();
    }

    // ── 관리자: 신고 조회/처리 ──

    @Transactional(readOnly = true)
    public List<ReportResponse> getReportsForAdmin(ReportStatus status) {
        List<Report> reports = (status == null)
                ? reportRepository.findAllByOrderByCreatedAtDesc()
                : reportRepository.findByStatusOrderByCreatedAtDesc(status);
        return reports.stream()
                .map(r -> ReportResponse.of(r, buildPreview(r.getTargetType(), r.getTargetId())))
                .toList();
    }

    private String buildPreview(ReportTargetType type, Long targetId) {
        if (type == ReportTargetType.POST) {
            return postRepository.findById(targetId)
                    .map(p -> "[게시글] " + p.getTitle())
                    .orElse("[게시글] (삭제됨)");
        } else if (type == ReportTargetType.COMMENT) {
            return commentRepository.findById(targetId)
                    .map(c -> {
                        String content = c.getContent();
                        if (content.length() > 40) content = content.substring(0, 40) + "…";
                        return "[댓글] " + content;
                    })
                    .orElse("[댓글] (삭제됨)");
        } else {
            return userRepository.findById(targetId)
                    .map(u -> "[사용자] " + u.getNickname())
                    .orElse("[사용자] (삭제됨)");
        }
    }

    @Transactional
    public ReportResponse processReport(Long reportId, ReportStatus action, Long adminId) {
        if (action == ReportStatus.PENDING) {
            throw new IllegalArgumentException("PENDING은 처리 결과로 사용할 수 없습니다");
        }

        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("신고를 찾을 수 없습니다"));

        if (report.getStatus() != ReportStatus.PENDING) {
            throw new IllegalArgumentException("이미 처리된 신고입니다");
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 처리 결과에 따른 부수 작업
        if (action == ReportStatus.DELETED) {
            hideTargetByAdmin(report.getTargetType(), report.getTargetId());
        }

        report.setStatus(action);
        report.setProcessedBy(admin);
        report.setProcessedAt(LocalDateTime.now());
        Report processed = reportRepository.save(report);

        // 신고자에게 처리 결과 알림
        notificationService.createNotification(
                report.getReporter().getId(),
                NotificationType.REPORT_PROCESSED,
                "신고하신 내용이 처리되었습니다: " + statusLabel(action),
                report.getId()
        );

        log.info("Report processed: id={}, action={}, admin={}", reportId, action, adminId);
        return ReportResponse.of(processed, buildPreview(processed.getTargetType(), processed.getTargetId()));
    }

    private void hideTargetByAdmin(ReportTargetType type, Long targetId) {
        if (type == ReportTargetType.POST) {
            postRepository.findById(targetId).ifPresent(post -> {
                Post hidden = Post.builder()
                        .id(post.getId()).author(post.getAuthor())
                        .title(post.getTitle()).content(post.getContent())
                        .category(post.getCategory()).viewCount(post.getViewCount())
                        .likeCount(post.getLikeCount()).reportCount(post.getReportCount())
                        .hidden(true).build();
                postRepository.save(hidden);
            });
        } else if (type == ReportTargetType.COMMENT) {
            commentRepository.findById(targetId).ifPresent(comment -> {
                Comment hidden = Comment.builder()
                        .id(comment.getId()).post(comment.getPost())
                        .author(comment.getAuthor()).content(comment.getContent())
                        .reportCount(comment.getReportCount()).hidden(true).build();
                commentRepository.save(hidden);
            });
        }
        // USER 대상은 별도 정지 필드가 없어 알림만 처리
    }

    private String statusLabel(ReportStatus status) {
        return switch (status) {
            case DISMISSED -> "기각";
            case DELETED -> "콘텐츠 삭제";
            case SUSPENDED -> "사용자 정지";
            default -> status.name();
        };
    }
}
