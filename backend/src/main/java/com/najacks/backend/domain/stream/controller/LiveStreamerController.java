package com.najacks.backend.domain.stream.controller;

import com.najacks.backend.domain.chat.entity.StreamerPremiumFeature;
import com.najacks.backend.domain.chat.repository.StreamerPremiumFeatureRepository;
import com.najacks.backend.domain.stream.dto.LiveStreamerResponse;
import com.najacks.backend.domain.stream.entity.LiveStatus;
import com.najacks.backend.domain.stream.entity.StreamerLiveState;
import com.najacks.backend.domain.stream.repository.StreamerLiveStateRepository;
import com.najacks.backend.domain.user.entity.StreamerProfile;
import com.najacks.backend.domain.user.entity.User;
import com.najacks.backend.domain.user.repository.StreamerProfileRepository;
import com.najacks.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/public/streamers")
@RequiredArgsConstructor
public class LiveStreamerController {

    private final StreamerLiveStateRepository stateRepo;
    private final StreamerProfileRepository profileRepo;
    private final UserRepository userRepo;
    private final StreamerPremiumFeatureRepository premiumRepo;

    /**
     * 현재 라이브 중인 스트리머 목록.
     * @param oauthOnly true면 치지직 OAuth 연결한 스트리머만 반환 (메인 히어로 용).
     *                  false(기본)면 LIVE 감지된 전부 (스트리머 목록 페이지 뱃지 용).
     */
    @GetMapping("/live")
    @Transactional(readOnly = true)
    public ResponseEntity<List<LiveStreamerResponse>> live(
            @RequestParam(name = "oauthOnly", defaultValue = "false") boolean oauthOnly) {
        List<StreamerLiveState> states = stateRepo.findByCurrentLiveStatus(LiveStatus.LIVE);
        List<LiveStreamerResponse> result = new ArrayList<>();
        for (StreamerLiveState s : states) {
            if (oauthOnly) {
                StreamerPremiumFeature pf = premiumRepo.findById(s.getStreamerNo()).orElse(null);
                if (pf == null || pf.getChzzkAccessTokenEnc() == null) continue;
            }
            User u = userRepo.findById(s.getStreamerNo()).orElse(null);
            if (u == null) continue;
            StreamerProfile p = profileRepo.findByUserId(s.getStreamerNo()).orElse(null);
            result.add(LiveStreamerResponse.builder()
                    .streamerId(u.getId())
                    .nickname(u.getNickname())
                    .profileImage(p != null && p.getAvatar() != null ? p.getAvatar() : u.getProfileImage())
                    .chzzkUrl(p != null ? p.getChzzkUrl() : null)
                    .liveTitle(s.getCurrentStreamTitle())
                    .liveCategory(s.getCurrentStreamCategory())
                    .viewerCount(s.getCurrentViewerCount())
                    .startedAt(s.getCurrentStreamStartedAt())
                    .build());
        }
        result.sort(Comparator.comparing(
                LiveStreamerResponse::getViewerCount,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return ResponseEntity.ok(result);
    }
}
