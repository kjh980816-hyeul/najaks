package com.najacks.backend.domain.chat.controller;

import com.najacks.backend.domain.chat.chzzk.ChzzkOAuthClient;
import com.najacks.backend.domain.chat.crypto.TokenCryptor;
import com.najacks.backend.domain.stream.external.ChzzkApiClient;
import com.najacks.backend.domain.chat.entity.StreamAnalysisReport;
import com.najacks.backend.domain.chat.entity.StreamerPremiumFeature;
import com.najacks.backend.domain.chat.repository.StreamAnalysisReportRepository;
import com.najacks.backend.domain.chat.repository.StreamerPremiumFeatureRepository;
import com.najacks.backend.domain.chat.service.EmailReportService;
import com.najacks.backend.domain.chat.service.PremiumGateService;
import com.najacks.backend.domain.stream.event.StreamEndedEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.najacks.backend.domain.user.entity.StreamerProfile;
import com.najacks.backend.domain.user.entity.User;
import com.najacks.backend.domain.user.repository.StreamerProfileRepository;
import com.najacks.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/premium")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PremiumController {

    private final PremiumGateService gateService;
    private final TokenCryptor tokenCryptor;
    private final StreamerPremiumFeatureRepository premiumRepo;
    private final UserRepository userRepo;
    private final EmailReportService emailReportService;
    private final StreamAnalysisReportRepository analysisRepo;
    private final ApplicationEventPublisher publisher;
    private final ChzzkOAuthClient chzzkOAuth;
    private final ChzzkApiClient chzzkApi;
    private final StreamerProfileRepository profileRepo;

    /** 프리미엄 관리 목록: 모든 프리미엄 행 + 스트리머 정보 */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list() {
        List<StreamerPremiumFeature> all = premiumRepo.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (StreamerPremiumFeature f : all) {
            Map<String, Object> row = new HashMap<>();
            User u = userRepo.findById(f.getStreamerNo()).orElse(null);
            row.put("streamerNo", f.getStreamerNo());
            row.put("nickname", u != null ? u.getNickname() : null);
            row.put("email", u != null ? u.getEmail() : null);
            row.put("chatAnalysisEnabled", f.getChatAnalysisEnabled());
            row.put("emailEnabled", f.getEmailEnabled());
            row.put("activatedAt", f.getActivatedAt());
            row.put("expiresAt", f.getExpiresAt());
            row.put("chzzkConnected", f.getChzzkAccessTokenEnc() != null);
            row.put("hasReportEmail", f.getReportEmailEnc() != null);
            row.put("lastReportSentAt", f.getLastReportSentAt());
            row.put("emailFailureCount", f.getEmailFailureCount());
            result.add(row);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{streamerNo}")
    public ResponseEntity<StreamerPremiumFeature> get(@PathVariable Long streamerNo) {
        return ResponseEntity.ok(gateService.getOrCreate(streamerNo));
    }

    @PostMapping("/{streamerNo}/toggle-chat-analysis")
    @Transactional
    public ResponseEntity<StreamerPremiumFeature> toggleChatAnalysis(
            @PathVariable Long streamerNo,
            @RequestParam boolean enabled,
            @RequestParam(required = false) Integer days) {
        StreamerPremiumFeature f = gateService.getOrCreate(streamerNo);
        f.setChatAnalysisEnabled(enabled);
        if (enabled) {
            f.setActivatedAt(LocalDateTime.now());
            if (days != null && days > 0) f.setExpiresAt(LocalDateTime.now().plusDays(days));
        } else {
            f.setExpiresAt(null);
        }
        return ResponseEntity.ok(premiumRepo.save(f));
    }

    @PostMapping("/{streamerNo}/report-email")
    @Transactional
    public ResponseEntity<StreamerPremiumFeature> setReportEmail(
            @PathVariable Long streamerNo,
            @RequestBody Map<String, String> body) {
        StreamerPremiumFeature f = gateService.getOrCreate(streamerNo);
        String email = body.get("email");
        if (email != null && !email.isBlank() && tokenCryptor.isConfigured()) {
            f.setReportEmailEnc(tokenCryptor.encrypt(email.trim()));
            f.setEmailEnabled(true);
            f.resetEmailFailure();
        } else {
            f.setReportEmailEnc(null);
        }
        return ResponseEntity.ok(premiumRepo.save(f));
    }

    @PostMapping("/{streamerNo}/toggle-email")
    @Transactional
    public ResponseEntity<StreamerPremiumFeature> toggleEmail(
            @PathVariable Long streamerNo,
            @RequestParam boolean enabled) {
        StreamerPremiumFeature f = gateService.getOrCreate(streamerNo);
        f.setEmailEnabled(enabled);
        if (enabled) f.resetEmailFailure();
        return ResponseEntity.ok(premiumRepo.save(f));
    }

    /** 최신 분석 리포트 재생성 (기존 삭제 → StreamEndedEvent 재발행 → 새로 AI 요약 + 이메일). */
    @PostMapping("/{streamerNo}/regenerate-last-report")
    @Transactional
    public ResponseEntity<Map<String, Object>> regenerateLastReport(@PathVariable Long streamerNo) {
        StreamAnalysisReport report = analysisRepo.findTopByStreamerNoOrderByIdDesc(streamerNo);
        Map<String, Object> body = new HashMap<>();
        if (report == null) {
            body.put("ok", false);
            body.put("message", "분석 리포트 없음");
            return ResponseEntity.ok(body);
        }
        String streamId = report.getStreamId();
        analysisRepo.delete(report);
        publisher.publishEvent(new StreamEndedEvent(streamerNo, streamId));
        body.put("ok", true);
        body.put("streamId", streamId);
        return ResponseEntity.ok(body);
    }

    /**
     * OAuth 연동된 모든 스트리머에 대해 치지직 본인 채널 정보 소급 조회.
     * chzzk_channel_id/name 채움 + StreamerProfile.chzzkUrl 이 사실상 비어있으면 자동 채움.
     */
    @PostMapping("/backfill-chzzk-channel-info")
    @Transactional
    public ResponseEntity<Map<String, Object>> backfillChzzkChannelInfo() {
        List<StreamerPremiumFeature> targets = premiumRepo.findAll().stream()
                .filter(f -> f.getChzzkAccessTokenEnc() != null)
                .toList();
        int ok = 0, skipped = 0, failed = 0, urlFilled = 0, avatarFilled = 0;
        List<String> details = new ArrayList<>();
        for (StreamerPremiumFeature f : targets) {
            try {
                String token = tokenCryptor.decrypt(f.getChzzkAccessTokenEnc());
                ChzzkOAuthClient.ChannelInfo ch = chzzkOAuth.fetchUserInfo(token);
                if (ch == null) {
                    skipped++;
                    details.add(f.getStreamerNo() + ": fetchUserInfo null (scope 미포함 가능)");
                    continue;
                }
                f.setChzzkChannelId(ch.getChannelId());
                f.setChzzkChannelName(ch.getChannelName());
                premiumRepo.save(f);

                ChzzkApiClient.ChannelDetail detail = chzzkApi.fetchChannelDetail(ch.getChannelId());
                String chzzkImageUrl = detail != null ? detail.getChannelImageUrl() : null;

                StreamerProfile profile = profileRepo.findByUserId(f.getStreamerNo()).orElse(null);
                if (profile != null) {
                    boolean urlBlank = ChzzkOAuthController.isChzzkUrlEffectivelyBlank(profile.getChzzkUrl());
                    boolean avatarBlank = ChzzkOAuthController.isBlank(profile.getAvatar());
                    boolean needAvatar = avatarBlank && chzzkImageUrl != null;
                    if (urlBlank || needAvatar) {
                        String newUrl = urlBlank ? "https://chzzk.naver.com/" + ch.getChannelId() : profile.getChzzkUrl();
                        String newAvatar = needAvatar ? chzzkImageUrl : profile.getAvatar();
                        StreamerProfile updated = StreamerProfile.builder()
                                .id(profile.getId())
                                .user(profile.getUser())
                                .coverImage(profile.getCoverImage())
                                .avatar(newAvatar)
                                .bio(profile.getBio())
                                .broadcastSchedule(profile.getBroadcastSchedule())
                                .youtubeUrl(profile.getYoutubeUrl())
                                .chzzkUrl(newUrl)
                                .soopUrl(profile.getSoopUrl())
                                .scheduleImageUrl(profile.getScheduleImageUrl())
                                .category(profile.getCategory())
                                .verified(profile.getVerified())
                                .build();
                        profileRepo.save(updated);
                        if (urlBlank) urlFilled++;
                        if (needAvatar) avatarFilled++;
                    }
                }
                ok++;
                details.add(f.getStreamerNo() + ": " + ch.getChannelId() + " / " + ch.getChannelName()
                        + (chzzkImageUrl != null ? " / img✓" : " / img✗"));
            } catch (Exception e) {
                failed++;
                details.add(f.getStreamerNo() + ": 실패 " + e.getMessage());
            }
        }
        Map<String, Object> body = new HashMap<>();
        body.put("total", targets.size());
        body.put("ok", ok);
        body.put("skipped", skipped);
        body.put("failed", failed);
        body.put("urlFilled", urlFilled);
        body.put("avatarFilled", avatarFilled);
        body.put("details", details);
        return ResponseEntity.ok(body);
    }

    /** 최신 분석 리포트의 Notion 페이지 본문만 append. 이메일은 발송하지 않음. 이미 이메일 받은 건에 대해 Notion 본문 채우는 용. 여러 번 호출 시 블록 중복 추가됨. */
    @PostMapping("/{streamerNo}/sync-last-report-notion")
    public ResponseEntity<Map<String, Object>> syncLastReportNotion(@PathVariable Long streamerNo) {
        StreamAnalysisReport report = analysisRepo.findTopByStreamerNoOrderByIdDesc(streamerNo);
        Map<String, Object> body = new HashMap<>();
        if (report == null) {
            body.put("ok", false);
            body.put("message", "분석 리포트 없음");
            return ResponseEntity.ok(body);
        }
        boolean ok = emailReportService.syncNotionBody(report.getId(), streamerNo);
        body.put("ok", ok);
        body.put("reportId", report.getId());
        body.put("notionPageId", report.getNotionPageId());
        return ResponseEntity.ok(body);
    }

    /** 최신 분석 리포트를 이메일로 재발송. SMTP 복구 후 재시도용. */
    @PostMapping("/{streamerNo}/resend-last-report")
    public ResponseEntity<Map<String, Object>> resendLastReport(@PathVariable Long streamerNo) {
        StreamAnalysisReport report = analysisRepo.findTopByStreamerNoOrderByIdDesc(streamerNo);
        Map<String, Object> body = new HashMap<>();
        if (report == null) {
            body.put("ok", false);
            body.put("message", "해당 스트리머의 분석 리포트 없음");
            return ResponseEntity.ok(body);
        }
        emailReportService.send(report.getId(), streamerNo);
        body.put("ok", true);
        body.put("reportId", report.getId());
        return ResponseEntity.ok(body);
    }
}
