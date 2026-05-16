package com.najacks.backend.domain.stream.service;

import com.najacks.backend.domain.stream.entity.StreamerLiveState;
import com.najacks.backend.domain.stream.external.ChzzkApiClient;
import com.najacks.backend.domain.stream.external.ChzzkLiveDetail;
import com.najacks.backend.domain.stream.repository.StreamerLiveStateRepository;
import com.najacks.backend.domain.stream.util.ChzzkUrlParser;
import com.najacks.backend.domain.user.entity.Role;
import com.najacks.backend.domain.user.entity.StreamerProfile;
import com.najacks.backend.domain.user.entity.User;
import com.najacks.backend.domain.user.repository.StreamerProfileRepository;
import com.najacks.backend.domain.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class StreamPollScheduler {

    private final StreamerProfileRepository profileRepo;
    private final StreamerLiveStateRepository stateRepo;
    private final UserRepository userRepository;
    private final ChzzkApiClient chzzk;
    private final StreamStateDiffService diffService;

    @Value("${app.stream.poll.enabled:true}")
    private boolean enabled;

    public StreamPollScheduler(StreamerProfileRepository profileRepo,
                               StreamerLiveStateRepository stateRepo,
                               UserRepository userRepository,
                               ChzzkApiClient chzzk,
                               StreamStateDiffService diffService) {
        this.profileRepo = profileRepo;
        this.stateRepo = stateRepo;
        this.userRepository = userRepository;
        this.chzzk = chzzk;
        this.diffService = diffService;
    }

    /**
     * 5분마다 치지직 전체 라이브 목록을 1회 수집 후 추적 스트리머들과 매칭.
     * 공식 Open API에 per-channel live-detail 엔드포인트가 없어, /open/v1/lives 페이지네이션이 유일한 공식 경로.
     */
    @Scheduled(fixedDelayString = "300000", initialDelay = 60000)
    public void pollAll() {
        if (!enabled) return;
        if (!chzzk.isConfigured()) {
            log.info("치지직 API 미설정 — 폴링 스킵");
            return;
        }

        List<StreamerProfile> profiles = profileRepo.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getVerified()))
                .filter(p -> p.getChzzkUrl() != null && !p.getChzzkUrl().isBlank())
                .toList();

        if (profiles.isEmpty()) return;

        Map<String, ChzzkLiveDetail> liveMap = chzzk.fetchLiveChannelMap();
        log.info("치지직 폴링: 추적 스트리머 {}명, 현재 전체 라이브 {}채널", profiles.size(), liveMap.size());

        for (StreamerProfile profile : profiles) {
            try {
                pollOne(profile, liveMap);
            } catch (Exception e) {
                log.warn("개별 상태 반영 예외 streamerNo={}", profile.getUser().getId(), e);
            }
        }
    }

    private void pollOne(StreamerProfile profile, Map<String, ChzzkLiveDetail> liveMap) {
        String channelId = ChzzkUrlParser.extractChannelId(profile.getChzzkUrl());
        if (channelId == null) return;
        Long streamerNo = profile.getUser().getId();

        StreamerLiveState state = stateRepo.findById(streamerNo).orElseGet(() -> {
            StreamerLiveState fresh = StreamerLiveState.builder()
                    .streamerNo(streamerNo)
                    .platform("CHZZK")
                    .chzzkChannelId(channelId)
                    .build();
            return stateRepo.save(fresh);
        });

        if (state.getChzzkChannelId() == null || !state.getChzzkChannelId().equals(channelId)) {
            state.setChzzkChannelId(channelId);
        }

        ChzzkLiveDetail detail = liveMap.get(channelId);
        if (detail == null) {
            detail = ChzzkLiveDetail.builder()
                    .channelId(channelId)
                    .status("CLOSE")
                    .build();
        }
        diffService.process(state, detail);
    }

    /** 스트리머 승인/등록 직후 초기 상태 행을 보장 */
    public void ensureLiveStateRow(User user) {
        if (user == null || user.getRole() != Role.STREAMER) return;
        if (stateRepo.existsById(user.getId())) return;
        profileRepo.findByUserId(user.getId()).ifPresent(profile -> {
            String channelId = ChzzkUrlParser.extractChannelId(profile.getChzzkUrl());
            stateRepo.save(StreamerLiveState.builder()
                    .streamerNo(user.getId())
                    .platform("CHZZK")
                    .chzzkChannelId(channelId)
                    .build());
        });
    }
}
