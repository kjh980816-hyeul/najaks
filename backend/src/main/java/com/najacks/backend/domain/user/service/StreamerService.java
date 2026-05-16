package com.najacks.backend.domain.user.service;

import com.najacks.backend.domain.clip.repository.ClipRepository;
import com.najacks.backend.domain.content.repository.ContentRepository;
import com.najacks.backend.domain.user.dto.*;
import com.najacks.backend.domain.user.entity.*;
import com.najacks.backend.domain.user.event.StreamerApprovedEvent;
import com.najacks.backend.domain.user.repository.StreamerApplicationRepository;
import com.najacks.backend.domain.user.repository.StreamerProfileRepository;
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
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamerService {

    private final UserRepository userRepository;
    private final StreamerProfileRepository streamerProfileRepository;
    private final StreamerApplicationRepository streamerApplicationRepository;
    private final ContentRepository contentRepository;
    private final ClipRepository clipRepository;
    private final ApplicationEventPublisher eventPublisher;

    private StreamerProfileDto toDtoWithCounts(StreamerProfile profile) {
        Long uid = profile.getUser().getId();
        long contentCount = contentRepository.countByStreamerId(uid);
        long clipCount = clipRepository.countByStreamerId(uid);
        return StreamerProfileDto.from(profile, contentCount, clipCount);
    }

    // ── 스트리머 목록 (공개) ──

    @Transactional(readOnly = true)
    public List<StreamerProfileDto> getVerifiedStreamers() {
        return streamerProfileRepository.findAll().stream()
                .filter(StreamerProfile::getVerified)
                .map(this::toDtoWithCounts)
                .toList();
    }

    @Transactional(readOnly = true)
    public StreamerProfileDto getStreamerProfile(Long userId) {
        StreamerProfile profile = streamerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return toDtoWithCounts(profile);
    }

    // ── 스트리머 프로필 편집 ──

    @Transactional
    public StreamerProfileDto updateStreamerProfile(Long userId, StreamerProfileUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (user.getRole() != Role.STREAMER) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        StreamerProfile profile = streamerProfileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    StreamerProfile newProfile = StreamerProfile.builder()
                            .user(user)
                            .verified(false)
                            .build();
                    return streamerProfileRepository.save(newProfile);
                });

        // StreamerProfile에 Setter가 없으므로 새로 빌드
        StreamerProfile updated = StreamerProfile.builder()
                .id(profile.getId())
                .user(profile.getUser())
                .coverImage(request.coverImage() != null ? request.coverImage() : profile.getCoverImage())
                .avatar(request.avatar() != null ? request.avatar() : profile.getAvatar())
                .bio(request.bio() != null ? request.bio() : profile.getBio())
                .broadcastSchedule(request.broadcastSchedule() != null ? request.broadcastSchedule() : profile.getBroadcastSchedule())
                .youtubeUrl(request.youtubeUrl() != null ? request.youtubeUrl() : profile.getYoutubeUrl())
                .chzzkUrl(request.chzzkUrl() != null ? request.chzzkUrl() : profile.getChzzkUrl())
                .soopUrl(request.soopUrl() != null ? request.soopUrl() : profile.getSoopUrl())
                .scheduleImageUrl(request.scheduleImageUrl() != null ? request.scheduleImageUrl() : profile.getScheduleImageUrl())
                .category(request.category() != null ? request.category() : profile.getCategory())
                .verified(profile.getVerified())
                .build();

        streamerProfileRepository.save(updated);
        return toDtoWithCounts(updated);
    }

    // ── 스트리머 인증 신청 ──

    @Transactional
    public StreamerApplicationResponse applyForStreamer(Long userId, StreamerApplicationRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 이미 인증 완료된 스트리머인 경우
        if (user.getRole() == Role.STREAMER) {
            boolean alreadyVerified = streamerProfileRepository.findByUserId(userId)
                    .map(sp -> Boolean.TRUE.equals(sp.getVerified()))
                    .orElse(false);
            if (alreadyVerified) {
                throw new IllegalArgumentException("이미 스트리머로 인증된 계정입니다");
            }
        }

        java.util.List<String> urls = request.screenshotUrls();
        if (urls == null || urls.isEmpty()) {
            throw new IllegalArgumentException("스크린샷이 1장 이상 필요합니다");
        }

        // 기존 신청이 있으면 상태에 따라 처리
        Optional<StreamerApplication> existingOpt = streamerApplicationRepository.findByUserId(userId);
        if (existingOpt.isPresent()) {
            StreamerApplication existing = existingOpt.get();
            if (existing.getStatus() == ApplicationStatus.PENDING) {
                throw new IllegalArgumentException("이미 대기 중인 신청이 있습니다");
            }
            if (existing.getStatus() == ApplicationStatus.APPROVED) {
                throw new IllegalArgumentException("이미 승인된 신청이 있습니다");
            }
            // REJECTED 신청 → 기존 레코드를 PENDING 으로 재사용
            StreamerApplication updated = StreamerApplication.builder()
                    .id(existing.getId())
                    .user(existing.getUser())
                    .screenshotUrl(urls.get(0))
                    .screenshotUrls(new java.util.ArrayList<>(urls))
                    .status(ApplicationStatus.PENDING)
                    .reviewedBy(null)
                    .reviewedAt(null)
                    .rejectionReason(null)
                    .build();
            StreamerApplication saved = streamerApplicationRepository.save(updated);
            return StreamerApplicationResponse.from(saved);
        }

        StreamerApplication application = StreamerApplication.builder()
                .user(user)
                .screenshotUrl(urls.get(0))
                .screenshotUrls(new java.util.ArrayList<>(urls))
                .status(ApplicationStatus.PENDING)
                .build();

        StreamerApplication saved = streamerApplicationRepository.save(application);
        return StreamerApplicationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public StreamerApplicationResponse getMyApplication(Long userId) {
        StreamerApplication application = streamerApplicationRepository.findByUserId(userId)
                .orElse(null);
        return application != null ? StreamerApplicationResponse.from(application) : null;
    }

    // ── 관리자: 스트리머 승인/반려 ──

    @Transactional(readOnly = true)
    public List<StreamerApplicationResponse> getPendingApplications() {
        return streamerApplicationRepository.findByStatus(ApplicationStatus.PENDING).stream()
                .map(StreamerApplicationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StreamerApplicationResponse> getAllApplications() {
        return streamerApplicationRepository.findAll().stream()
                .map(StreamerApplicationResponse::from)
                .toList();
    }

    @Transactional
    public StreamerApplicationResponse approveApplication(Long applicationId, Long adminId) {
        StreamerApplication application = streamerApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("신청을 찾을 수 없습니다"));

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        User applicant = application.getUser();

        // 역할을 STREAMER로 변경
        applicant.setRole(Role.STREAMER);
        userRepository.save(applicant);

        // 스트리머 프로필 생성 또는 verified 업데이트
        StreamerProfile profile = streamerProfileRepository.findByUserId(applicant.getId())
                .orElseGet(() -> streamerProfileRepository.save(
                        StreamerProfile.builder().user(applicant).build()
                ));
        profile.setVerified(true);
        streamerProfileRepository.save(profile);

        // Notion 스트리머 DB에 자동 등록 (AFTER_COMMIT 리스너가 처리)
        eventPublisher.publishEvent(new StreamerApprovedEvent(applicant.getId()));

        // 신청 상태 업데이트 — Builder로 새 객체 생성
        StreamerApplication updated = StreamerApplication.builder()
                .id(application.getId())
                .user(application.getUser())
                .screenshotUrl(application.getScreenshotUrl())
                .screenshotUrls(new java.util.ArrayList<>(application.getScreenshotUrls()))
                .status(ApplicationStatus.APPROVED)
                .reviewedBy(admin)
                .reviewedAt(LocalDateTime.now())
                .build();

        streamerApplicationRepository.save(updated);
        log.info("Streamer application approved: userId={}, applicationId={}", applicant.getId(), applicationId);
        return StreamerApplicationResponse.from(updated);
    }

    @Transactional
    public StreamerApplicationResponse rejectApplication(Long applicationId, Long adminId, String reason) {
        StreamerApplication application = streamerApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("신청을 찾을 수 없습니다"));

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        StreamerApplication updated = StreamerApplication.builder()
                .id(application.getId())
                .user(application.getUser())
                .screenshotUrl(application.getScreenshotUrl())
                .screenshotUrls(new java.util.ArrayList<>(application.getScreenshotUrls()))
                .status(ApplicationStatus.REJECTED)
                .reviewedBy(admin)
                .reviewedAt(LocalDateTime.now())
                .rejectionReason(reason)
                .build();

        streamerApplicationRepository.save(updated);
        log.info("Streamer application rejected: applicationId={}", applicationId);
        return StreamerApplicationResponse.from(updated);
    }
}
