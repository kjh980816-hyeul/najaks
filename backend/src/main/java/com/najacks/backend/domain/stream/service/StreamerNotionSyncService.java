package com.najacks.backend.domain.stream.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.najacks.backend.domain.chat.repository.StreamerPremiumFeatureRepository;
import com.najacks.backend.domain.stream.entity.StreamerLiveState;
import com.najacks.backend.domain.stream.external.ChzzkLiveDetail;
import com.najacks.backend.domain.stream.repository.StreamerLiveStateRepository;
import com.najacks.backend.domain.stream.util.ChzzkUrlParser;
import com.najacks.backend.domain.user.entity.Role;
import com.najacks.backend.domain.user.entity.StreamerProfile;
import com.najacks.backend.domain.user.entity.User;
import com.najacks.backend.domain.user.event.StreamerApprovedEvent;
import com.najacks.backend.domain.user.repository.StreamerProfileRepository;
import com.najacks.backend.domain.user.repository.UserRepository;
import com.najacks.backend.notion.NotionClient;
import com.najacks.backend.notion.NotionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class StreamerNotionSyncService {

    private final NotionClient notion;
    private final StreamerProfileRepository profileRepo;
    private final StreamerLiveStateRepository liveStateRepo;
    private final StreamerPremiumFeatureRepository premiumRepo;
    private final UserRepository userRepo;

    @Value("${app.notion.database.streamers:}")
    private String dbId;

    @Value("${app.site.base-url:https://najaks.co.kr}")
    private String siteBaseUrl;

    public boolean isConfigured() {
        return notion.isConfigured() && dbId != null && !dbId.isBlank();
    }

    public String ensureStreamerPage(StreamerLiveState state, User user, StreamerProfile profile) {
        if (!isConfigured()) return null;
        if (state.getNotionPageId() != null) return state.getNotionPageId();

        boolean chzzkConnected = premiumRepo.findById(user.getId())
                .map(f -> f.getChzzkAccessTokenEnc() != null)
                .orElse(false);

        Map<String, String> schema = notion.getDatabaseSchema(dbId);
        Map<String, ObjectNode> props = filterBySchema(
                buildStreamerProperties(user, profile, false, chzzkConnected), schema);
        String avatar = pickAvatarUrl(user, profile);

        // 중복 방지: 같은 스트리머번호의 페이지가 이미 있는지 검색
        String existingPageId = notion.findPageIdByNumberProperty(dbId, "스트리머번호", user.getId());
        if (existingPageId != null) {
            notion.updatePageProperties(existingPageId, props, avatar);
            return existingPageId;
        }

        try {
            return notion.createPage(dbId, props, null, avatar);
        } catch (Exception e) {
            log.warn("Notion 스트리머 페이지 생성 실패 userId={}", user.getId(), e);
            return null;
        }
    }

    /** 프사/커버 중 우선순위: StreamerProfile.avatar → User.profileImage → StreamerProfile.coverImage */
    private String pickAvatarUrl(User user, StreamerProfile profile) {
        String raw = null;
        if (profile != null && profile.getAvatar() != null && !profile.getAvatar().isBlank()) raw = profile.getAvatar();
        else if (user != null && user.getProfileImage() != null && !user.getProfileImage().isBlank()) raw = user.getProfileImage();
        else if (profile != null && profile.getCoverImage() != null && !profile.getCoverImage().isBlank()) raw = profile.getCoverImage();
        return toAbsoluteUrl(raw);
    }

    private String toAbsoluteUrl(String path) {
        if (path == null || path.isBlank()) return null;
        if (path.startsWith("http://") || path.startsWith("https://")) return path;
        if (path.startsWith("/")) return siteBaseUrl + path;
        return siteBaseUrl + "/" + path;
    }

    private Map<String, ObjectNode> buildStreamerProperties(
            User user, StreamerProfile profile, boolean liveNow, boolean chzzkConnected) {
        Map<String, ObjectNode> props = new LinkedHashMap<>();
        props.put("닉네임", NotionProperties.title(user.getNickname()));
        props.put("플랫폼", NotionProperties.select(pickPlatform(profile)));
        props.put("채널 URL", NotionProperties.url(pickChannelUrl(profile)));
        props.put("스트리머번호", NotionProperties.number(user.getId()));
        props.put("현재 방송", NotionProperties.checkbox(liveNow));
        props.put("치지직 연동", NotionProperties.checkbox(chzzkConnected));
        props.put("가입일", NotionProperties.date(user.getCreatedAt() != null ? user.getCreatedAt().toLocalDate() : LocalDate.now()));
        props.put("상태", NotionProperties.select("활성"));
        if (profile != null && profile.getCategory() != null && !profile.getCategory().isBlank()) {
            props.put("카테고리", NotionProperties.select(profile.getCategory()));
        }
        if (profile != null && profile.getBio() != null && !profile.getBio().isBlank()) {
            props.put("자기소개", NotionProperties.richText(profile.getBio()));
        }
        return props;
    }

    /** Notion DB 스키마에 존재하는 속성만 남김 — 없는 속성은 400 방지 위해 스킵. */
    private Map<String, ObjectNode> filterBySchema(
            Map<String, ObjectNode> props, Map<String, String> schema) {
        if (schema.isEmpty()) return props;
        Map<String, ObjectNode> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, ObjectNode> e : props.entrySet()) {
            if (schema.containsKey(e.getKey())) {
                filtered.put(e.getKey(), e.getValue());
            }
        }
        return filtered;
    }

    private String pickPlatform(StreamerProfile profile) {
        if (profile == null) return "기타";
        if (profile.getChzzkUrl() != null && !profile.getChzzkUrl().isBlank()) return "CHZZK";
        if (profile.getYoutubeUrl() != null && !profile.getYoutubeUrl().isBlank()) return "YOUTUBE";
        if (profile.getSoopUrl() != null && !profile.getSoopUrl().isBlank()) return "SOOP";
        return "기타";
    }

    private String pickChannelUrl(StreamerProfile profile) {
        if (profile == null) return null;
        if (profile.getChzzkUrl() != null && !profile.getChzzkUrl().isBlank()) return profile.getChzzkUrl();
        if (profile.getYoutubeUrl() != null && !profile.getYoutubeUrl().isBlank()) return profile.getYoutubeUrl();
        if (profile.getSoopUrl() != null && !profile.getSoopUrl().isBlank()) return profile.getSoopUrl();
        return null;
    }

    /**
     * 스트리머 인증 승인 직후 Notion 자동 등록.
     * AFTER_COMMIT 이후 비동기 실행 — Notion 장애가 인증 자체엔 영향 없음.
     */
    @Async("notionSyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStreamerApproved(StreamerApprovedEvent ev) {
        try {
            syncOneStreamer(ev.streamerNo());
            log.info("✅ 신규 스트리머 Notion 자동 등록 완료 streamerNo={}", ev.streamerNo());
        } catch (Exception e) {
            log.warn("신규 스트리머 Notion 등록 실패 streamerNo={}", ev.streamerNo(), e);
        }
    }

    /** 단일 스트리머를 Notion에 upsert. state row가 없으면 함께 생성. */
    public void syncOneStreamer(Long streamerNo) {
        if (!isConfigured()) return;

        User user = userRepo.findById(streamerNo).orElse(null);
        if (user == null || user.getRole() != Role.STREAMER) return;

        StreamerProfile profile = profileRepo.findByUserId(streamerNo).orElse(null);
        if (profile == null || !Boolean.TRUE.equals(profile.getVerified())) return;

        StreamerLiveState state = liveStateRepo.findById(streamerNo).orElseGet(() -> {
            String channelId = ChzzkUrlParser.extractChannelId(profile.getChzzkUrl());
            return liveStateRepo.save(StreamerLiveState.builder()
                    .streamerNo(streamerNo)
                    .platform("CHZZK")
                    .chzzkChannelId(channelId)
                    .build());
        });

        boolean liveNow = state.getCurrentLiveStatus() != null
                && state.getCurrentLiveStatus().name().equals("LIVE");
        boolean chzzkConnected = premiumRepo.findById(streamerNo)
                .map(f -> f.getChzzkAccessTokenEnc() != null)
                .orElse(false);

        Map<String, String> schema = notion.getDatabaseSchema(dbId);
        Map<String, ObjectNode> props = filterBySchema(
                buildStreamerProperties(user, profile, liveNow, chzzkConnected), schema);
        String avatar = pickAvatarUrl(user, profile);

        // 1) state에 저장된 pageId 우선 → 없으면 Notion DB 내 검색 (중복 방지)
        String pageId = state.getNotionPageId();
        if (pageId == null) {
            pageId = notion.findPageIdByNumberProperty(dbId, "스트리머번호", streamerNo);
        }

        if (pageId != null) {
            notion.updatePageProperties(pageId, props, avatar);
            if (!pageId.equals(state.getNotionPageId())) {
                state.setNotionPageId(pageId);
                liveStateRepo.save(state);
            }
        } else {
            String newId = notion.createPage(dbId, props, null, avatar);
            if (newId != null) {
                state.setNotionPageId(newId);
                liveStateRepo.save(state);
            }
        }
    }

    /**
     * 모든 verified 스트리머를 Notion 스트리머 DB에 일괄 동기화.
     * 이미 notion_page_id 가 있는 경우 속성만 업데이트, 없으면 신규 생성.
     * 반환: {created, updated, skipped, failed} 카운트
     */
    @Transactional
    public Map<String, Integer> syncAllStreamers() {
        int created = 0, updated = 0, skipped = 0, failed = 0;
        if (!isConfigured()) {
            Map<String, Integer> r = new LinkedHashMap<>();
            r.put("created", 0); r.put("updated", 0); r.put("skipped", 0); r.put("failed", 0);
            r.put("error", -1);
            return r;
        }

        // DB 스키마 1회 조회 — 없는 속성은 자동 스킵
        Map<String, String> schema = notion.getDatabaseSchema(dbId);
        log.info("Notion 스트리머 DB 스키마 속성: {}", schema.keySet());

        List<User> allStreamers = userRepo.findAll().stream()
                .filter(u -> u.getRole() == Role.STREAMER)
                .toList();

        for (User user : allStreamers) {
            try {
                StreamerProfile profile = profileRepo.findByUserId(user.getId()).orElse(null);
                if (profile == null || !Boolean.TRUE.equals(profile.getVerified())) {
                    skipped++;
                    continue;
                }

                StreamerLiveState state = liveStateRepo.findById(user.getId()).orElse(null);
                boolean liveNow = state != null
                        && state.getCurrentLiveStatus() != null
                        && state.getCurrentLiveStatus().name().equals("LIVE");
                boolean chzzkConnected = premiumRepo.findById(user.getId())
                        .map(f -> f.getChzzkAccessTokenEnc() != null)
                        .orElse(false);

                Map<String, ObjectNode> props = filterBySchema(
                        buildStreamerProperties(user, profile, liveNow, chzzkConnected), schema);

                String avatar = pickAvatarUrl(user, profile);

                // state 저장된 pageId 우선, 없으면 Notion DB 내 "스트리머번호" 기준 검색
                String existingPageId = state != null ? state.getNotionPageId() : null;
                if (existingPageId == null) {
                    existingPageId = notion.findPageIdByNumberProperty(dbId, "스트리머번호", user.getId());
                    if (existingPageId != null && state != null) {
                        state.setNotionPageId(existingPageId);
                        liveStateRepo.save(state);
                    }
                }

                if (existingPageId != null) {
                    notion.updatePageProperties(existingPageId, props, avatar);
                    updated++;
                } else {
                    String newPageId = notion.createPage(dbId, props, null, avatar);
                    if (newPageId != null && state != null) {
                        state.setNotionPageId(newPageId);
                        liveStateRepo.save(state);
                    }
                    created++;
                }
            } catch (Exception e) {
                failed++;
                log.warn("Notion 스트리머 동기화 실패 userId={} msg={}", user.getId(), e.getMessage());
            }
        }

        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("created", created);
        result.put("updated", updated);
        result.put("skipped", skipped);
        result.put("failed", failed);
        log.info("스트리머 Notion 일괄 동기화 완료: {}", result);
        return result;
    }

    public void updateLiveStatus(StreamerLiveState state, boolean live, ChzzkLiveDetail detail) {
        if (!isConfigured() || state.getNotionPageId() == null) return;
        Map<String, ObjectNode> props = new LinkedHashMap<>();
        props.put("현재 방송", NotionProperties.checkbox(live));
        if (live && detail != null && detail.getLiveTitle() != null) {
            props.put("최근 방송", NotionProperties.richText(detail.getLiveTitle()));
        }
        // DB 스키마에 없는 속성은 자동 스킵 — Notion DB 에 속성이 없어도 400 안 터지게
        Map<String, String> schema = notion.getDatabaseSchema(dbId);
        Map<String, ObjectNode> filtered = filterBySchema(props, schema);
        if (filtered.isEmpty()) return;
        try {
            notion.updatePageProperties(state.getNotionPageId(), filtered);
        } catch (Exception e) {
            log.warn("Notion 스트리머 라이브 상태 업데이트 실패 streamerNo={}", state.getStreamerNo(), e);
        }
    }
}
