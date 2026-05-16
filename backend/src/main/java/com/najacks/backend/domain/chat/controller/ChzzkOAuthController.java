package com.najacks.backend.domain.chat.controller;

import com.najacks.backend.auth.CustomUserPrincipal;
import com.najacks.backend.domain.chat.chzzk.ChzzkOAuthClient;
import com.najacks.backend.domain.chat.chzzk.OAuthStateStore;
import com.najacks.backend.domain.chat.crypto.TokenCryptor;
import com.najacks.backend.domain.chat.entity.StreamerPremiumFeature;
import com.najacks.backend.domain.chat.repository.StreamerPremiumFeatureRepository;
import com.najacks.backend.domain.chat.service.PremiumGateService;
import com.najacks.backend.domain.stream.external.ChzzkApiClient;
import com.najacks.backend.domain.user.entity.Role;
import com.najacks.backend.domain.user.entity.StreamerProfile;
import com.najacks.backend.domain.user.entity.User;
import com.najacks.backend.domain.user.repository.StreamerProfileRepository;
import com.najacks.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 치지직 OAuth 연동 엔드포인트.
 * 플로우:
 *  1. 프론트 → POST /api/chzzk/oauth/authorize-url  (JWT 필요)
 *     → state 생성 + 치지직 인가 URL 반환
 *  2. 프론트가 window.location.href 로 인가 URL 이동
 *  3. 치지직이 GET /oauth/chzzk/callback 으로 code + state 돌려줌 (permitAll)
 *  4. 서버에서 code → 토큰 교환 → 암호화 저장 → 프론트 redirect (/mypage?chzzk=connected|error)
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ChzzkOAuthController {

    private final ChzzkOAuthClient oauth;
    private final OAuthStateStore stateStore;
    private final PremiumGateService gateService;
    private final StreamerPremiumFeatureRepository premiumRepo;
    private final UserRepository userRepo;
    private final StreamerProfileRepository profileRepo;
    private final TokenCryptor cryptor;
    private final ChzzkApiClient chzzkApi;

    @Value("${app.site.base-url:https://najaks.co.kr}")
    private String siteBaseUrl;

    /** 1단계: 인가 URL 발급 (JWT 필요, STREAMER/ADMIN만) */
    @PostMapping("/api/chzzk/oauth/authorize-url")
    public ResponseEntity<Map<String, String>> authorizeUrl(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!oauth.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "치지직 OAuth 미설정"));
        }
        User user = userRepo.findById(principal.getId()).orElse(null);
        if (user == null || (user.getRole() != Role.STREAMER && user.getRole() != Role.ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String state = stateStore.issue(user.getId());
        String url = oauth.buildAuthorizeUrl(state);
        return ResponseEntity.ok(Map.of("authorizeUrl", url));
    }

    /** 2단계: 콜백 (permitAll, 치지직이 브라우저로 유저 리다이렉트 시킴) */
    @GetMapping("/oauth/chzzk/callback")
    @Transactional
    public RedirectView callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {

        if (error != null && !error.isBlank()) {
            return new RedirectView(siteBaseUrl + "/mypage?chzzk=denied");
        }
        if (code == null || state == null) {
            return new RedirectView(siteBaseUrl + "/mypage?chzzk=error");
        }

        Long streamerNo = stateStore.consume(state);
        if (streamerNo == null) {
            log.warn("치지직 콜백 state 검증 실패 state={}", state);
            return new RedirectView(siteBaseUrl + "/mypage?chzzk=expired");
        }

        try {
            ChzzkOAuthClient.TokenResponse token = oauth.exchangeCode(code, state);
            if (token.getAccessToken() == null) {
                log.warn("치지직 토큰 응답에 accessToken 없음 streamerNo={}", streamerNo);
                return new RedirectView(siteBaseUrl + "/mypage?chzzk=error");
            }

            StreamerPremiumFeature feature = gateService.getOrCreate(streamerNo);
            feature.setChzzkAccessTokenEnc(cryptor.encrypt(token.getAccessToken()));
            if (token.getRefreshToken() != null) {
                feature.setChzzkRefreshTokenEnc(cryptor.encrypt(token.getRefreshToken()));
            }
            if (token.getExpiresIn() > 0) {
                feature.setChzzkTokenExpiresAt(LocalDateTime.now().plusSeconds(token.getExpiresIn()));
            }
            if (token.getScope() != null) feature.setChzzkScope(token.getScope());

            // 본인 채널 정보 조회 — 실패해도 OAuth 연동 자체는 성공 처리
            ChzzkOAuthClient.ChannelInfo ch = oauth.fetchUserInfo(token.getAccessToken());
            if (ch != null) {
                feature.setChzzkChannelId(ch.getChannelId());
                feature.setChzzkChannelName(ch.getChannelName());
                // 채널 상세(프로필 이미지 URL) 추가 조회
                ChzzkApiClient.ChannelDetail detail = chzzkApi.fetchChannelDetail(ch.getChannelId());
                String chzzkImageUrl = detail != null ? detail.getChannelImageUrl() : null;
                // StreamerProfile 에 chzzkUrl/avatar 가 비어있으면 자동 채움 (기존 값은 보존)
                profileRepo.findByUserId(streamerNo).ifPresent(profile -> {
                    boolean urlBlank = isChzzkUrlEffectivelyBlank(profile.getChzzkUrl());
                    boolean avatarBlank = isBlank(profile.getAvatar());
                    if (!urlBlank && !(avatarBlank && chzzkImageUrl != null)) return;
                    String newUrl = urlBlank ? "https://chzzk.naver.com/" + ch.getChannelId() : profile.getChzzkUrl();
                    String newAvatar = (avatarBlank && chzzkImageUrl != null) ? chzzkImageUrl : profile.getAvatar();
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
                    log.info("치지직 프로필 자동 설정 streamerNo={} urlFilled={} avatarFilled={}",
                            streamerNo, urlBlank, avatarBlank && chzzkImageUrl != null);
                });
            }
            premiumRepo.save(feature);

            log.info("✅ 치지직 OAuth 연동 완료 streamerNo={} channelId={}", streamerNo,
                    ch != null ? ch.getChannelId() : "(미조회)");
            return new RedirectView(siteBaseUrl + "/mypage?chzzk=connected");
        } catch (Exception e) {
            log.warn("치지직 OAuth 토큰 교환 실패 streamerNo={}", streamerNo, e);
            return new RedirectView(siteBaseUrl + "/mypage?chzzk=error");
        }
    }

    /** 연동 상태 조회 */
    @GetMapping("/api/chzzk/oauth/status")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> status(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        StreamerPremiumFeature f = premiumRepo.findById(principal.getId()).orElse(null);
        Map<String, Object> body = new HashMap<>();
        boolean connected = f != null && f.getChzzkAccessTokenEnc() != null;
        body.put("connected", connected);
        body.put("scope", f != null ? f.getChzzkScope() : null);
        body.put("expiresAt", f != null ? f.getChzzkTokenExpiresAt() : null);
        body.put("chatAnalysisEnabled", f != null && Boolean.TRUE.equals(f.getChatAnalysisEnabled()));
        return ResponseEntity.ok(body);
    }

    static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** "URL은 있지만 channelId가 없는" 상태까지 비어있는 것으로 간주 */
    static boolean isChzzkUrlEffectivelyBlank(String url) {
        if (url == null || url.isBlank()) return true;
        String tail = url.replaceFirst("(?i)^https?://chzzk\\.naver\\.com/?", "").trim();
        return tail.isBlank();
    }

    /** 연동 해제 */
    @DeleteMapping("/api/chzzk/oauth/connection")
    @Transactional
    public ResponseEntity<Void> disconnect(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        StreamerPremiumFeature f = premiumRepo.findById(principal.getId()).orElse(null);
        if (f != null) {
            f.setChzzkAccessTokenEnc(null);
            f.setChzzkRefreshTokenEnc(null);
            f.setChzzkTokenExpiresAt(null);
            f.setChzzkScope(null);
            premiumRepo.save(f);
        }
        return ResponseEntity.noContent().build();
    }
}
