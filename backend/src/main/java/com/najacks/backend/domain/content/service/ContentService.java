package com.najacks.backend.domain.content.service;

import com.najacks.backend.domain.content.dto.ContentCreateRequest;
import com.najacks.backend.domain.content.dto.ContentResponse;
import com.najacks.backend.domain.content.entity.Content;
import com.najacks.backend.domain.content.entity.ContentCategory;
import com.najacks.backend.domain.content.entity.ContentStatus;
import com.najacks.backend.domain.content.repository.ContentRepository;
import com.najacks.backend.domain.user.entity.Role;
import com.najacks.backend.domain.user.entity.User;
import com.najacks.backend.domain.user.repository.UserRepository;
import com.najacks.backend.global.exception.CustomException;
import com.najacks.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentService {

    private final ContentRepository contentRepository;
    private final UserRepository userRepository;

    // ── 공개 API ──

    @Transactional(readOnly = true)
    public List<ContentResponse> getApprovedContents() {
        return contentRepository.findByStatusIn(
                List.of(ContentStatus.APPROVED, ContentStatus.ONGOING)
        ).stream().map(ContentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ContentResponse> getApprovedContentsByCategory(ContentCategory category) {
        return contentRepository.findByStatusInAndCategory(
                List.of(ContentStatus.APPROVED, ContentStatus.ONGOING), category
        ).stream().map(ContentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ContentResponse getContent(Long id) {
        Content content = contentRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTENT_NOT_FOUND));
        return ContentResponse.from(content);
    }

    @Transactional(readOnly = true)
    public List<ContentResponse> getContentsByStreamer(Long streamerId) {
        return contentRepository.findByStreamerId(streamerId).stream()
                .filter(c -> c.getStatus() == ContentStatus.APPROVED || c.getStatus() == ContentStatus.ONGOING)
                .map(ContentResponse::from)
                .toList();
    }

    // ── 스트리머: 컨텐츠 등록 ──

    @Transactional
    public ContentResponse createContent(Long userId, ContentCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (user.getRole() != Role.STREAMER && user.getRole() != Role.ADMIN) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        LocalDateTime startDt = null;
        LocalDateTime endDt = null;
        try { if (request.startDate() != null && !request.startDate().isBlank()) startDt = LocalDateTime.parse(request.startDate()); } catch (Exception ignored) {}
        try { if (request.endDate() != null && !request.endDate().isBlank()) endDt = LocalDateTime.parse(request.endDate()); } catch (Exception ignored) {}

        List<String> imageUrls = request.imageUrls() != null ? new ArrayList<>(request.imageUrls()) : new ArrayList<>();
        List<ContentCategory> tags = request.tags() != null ? new ArrayList<>(request.tags()) : new ArrayList<>();

        Content content = Content.builder()
                .streamer(user)
                .title(request.title())
                .description(request.description())
                .thumbnailUrl(request.thumbnailUrl())
                .imageUrls(imageUrls)
                .applyLink(request.applyLink())
                .startDate(startDt)
                .endDate(endDt)
                .category(request.category())
                .tags(tags)
                .requirements(request.requirements())
                .prize(request.prize())
                .recruitCount(request.recruitCount())
                .followerCount(request.followerCount())
                .followerUnlimited(Boolean.TRUE.equals(request.followerUnlimited()))
                .contactMethod(request.contactMethod())
                .contactInfo(request.contactInfo())
                .hostName(request.hostName())
                .status(ContentStatus.PENDING)
                .build();

        Content saved = contentRepository.save(content);
        log.info("Content created: id={}, streamer={}", saved.getId(), user.getNickname());
        return ContentResponse.from(saved);
    }

    @Transactional
    public ContentResponse updateContent(Long contentId, Long userId, ContentCreateRequest request) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTENT_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        boolean isOwner = content.getStreamer().getId().equals(userId);
        boolean isAdmin = user.getRole() == Role.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        LocalDateTime startDt = null;
        LocalDateTime endDt = null;
        try { if (request.startDate() != null && !request.startDate().isBlank()) startDt = LocalDateTime.parse(request.startDate()); } catch (Exception ignored) {}
        try { if (request.endDate() != null && !request.endDate().isBlank()) endDt = LocalDateTime.parse(request.endDate()); } catch (Exception ignored) {}

        List<String> imageUrls = request.imageUrls() != null ? new ArrayList<>(request.imageUrls()) : new ArrayList<>();
        List<ContentCategory> tags = request.tags() != null ? new ArrayList<>(request.tags()) : new ArrayList<>();

        content.updateFields(
                request.title(), request.description(), request.thumbnailUrl(), imageUrls,
                request.applyLink(), request.category(), tags,
                startDt, endDt,
                request.requirements(), request.prize(), request.recruitCount(),
                request.followerCount(), Boolean.TRUE.equals(request.followerUnlimited()),
                request.contactMethod(), request.contactInfo(), request.hostName()
        );

        log.info("Content updated: id={}, by userId={}", contentId, userId);
        return ContentResponse.from(content);
    }

    @Transactional(readOnly = true)
    public List<ContentResponse> getMyContents(Long userId) {
        return contentRepository.findByStreamerId(userId).stream()
                .map(ContentResponse::from)
                .toList();
    }

    // ── 관리자: 컨텐츠 승인/반려 ──

    @Transactional(readOnly = true)
    public List<ContentResponse> getPendingContents() {
        return contentRepository.findByStatus(ContentStatus.PENDING).stream()
                .map(ContentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ContentResponse> getAllContents() {
        return contentRepository.findAll().stream()
                .map(ContentResponse::from)
                .toList();
    }

    @Transactional
    public ContentResponse approveContent(Long contentId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTENT_NOT_FOUND));
        content.changeStatus(ContentStatus.APPROVED);
        log.info("Content approved: id={}", contentId);
        return ContentResponse.from(content);
    }

    @Transactional
    public ContentResponse rejectContent(Long contentId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTENT_NOT_FOUND));
        content.changeStatus(ContentStatus.REJECTED);
        log.info("Content rejected: id={}", contentId);
        return ContentResponse.from(content);
    }

    @Transactional
    public ContentResponse closeContent(Long contentId, Long userId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTENT_NOT_FOUND));

        if (!content.getStreamer().getId().equals(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        content.changeStatus(ContentStatus.CLOSED);
        return ContentResponse.from(content);
    }

    @Transactional
    public void deleteContent(Long contentId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTENT_NOT_FOUND));
        contentRepository.delete(content);
        log.info("Content deleted by admin: id={}", contentId);
    }

    @Scheduled(cron = "0 5 * * * *")
    @Transactional
    public void cleanupExpiredContents() {
        // KST "오늘 자정" 기준: 내일 KST 00:00 이 도래하면 "오늘 마감" 항목도 삭제
        java.time.LocalDate todayKst = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        LocalDateTime cutoff = todayKst.plusDays(1).atStartOfDay();
        List<Content> expired = contentRepository.findByEndDateIsNotNullAndEndDateBefore(cutoff);
        if (expired.isEmpty()) return;
        contentRepository.deleteAll(expired);
        log.info("Auto-cleanup: deleted {} expired contents (endDate<{} KST midnight): ids={}",
                expired.size(), cutoff, expired.stream().map(Content::getId).toList());
    }
}
