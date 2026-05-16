package com.najacks.backend.domain.search.controller;

import com.najacks.backend.domain.admin.entity.AdBanner;
import com.najacks.backend.domain.admin.entity.Notice;
import com.najacks.backend.domain.admin.entity.StaticPage;
import com.najacks.backend.domain.admin.service.AdminService;
import com.najacks.backend.domain.clip.dto.ClipResponse;
import com.najacks.backend.domain.clip.entity.Clip;
import com.najacks.backend.domain.clip.repository.ClipRepository;
import com.najacks.backend.domain.content.dto.ContentResponse;
import com.najacks.backend.domain.content.entity.Content;
import com.najacks.backend.domain.content.entity.ContentStatus;
import com.najacks.backend.domain.content.repository.ContentRepository;
import com.najacks.backend.domain.user.dto.StreamerProfileDto;
import com.najacks.backend.domain.user.entity.StreamerProfile;
import com.najacks.backend.domain.user.repository.StreamerProfileRepository;
import com.najacks.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class SearchController {

    private final StreamerProfileRepository streamerProfileRepository;
    private final ContentRepository contentRepository;
    private final ClipRepository clipRepository;
    private final AdminService adminService;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Map<String, Object>>> search(@RequestParam String q) {
        String query = q.toLowerCase();
        Map<String, Object> results = new HashMap<>();

        // 스트리머 검색
        List<StreamerProfileDto> streamers = streamerProfileRepository.findAll().stream()
                .filter(StreamerProfile::getVerified)
                .filter(s -> s.getUser().getNickname().toLowerCase().contains(query)
                        || (s.getBio() != null && s.getBio().toLowerCase().contains(query)))
                .map(StreamerProfileDto::from)
                .toList();
        results.put("streamers", streamers);

        // 컨텐츠 검색
        List<ContentResponse> contents = contentRepository.findByStatusIn(
                List.of(ContentStatus.APPROVED, ContentStatus.ONGOING)
        ).stream()
                .filter(c -> c.getTitle().toLowerCase().contains(query)
                        || (c.getDescription() != null && c.getDescription().toLowerCase().contains(query)))
                .map(ContentResponse::from)
                .toList();
        results.put("contents", contents);

        // 클립 검색
        List<ClipResponse> clips = clipRepository.findAll().stream()
                .filter(c -> c.getTitle().toLowerCase().contains(query))
                .map(ClipResponse::from)
                .toList();
        results.put("clips", clips);

        return ResponseEntity.ok(ApiResponse.success(results));
    }

    // ── 공개: 공지사항 ──

    @GetMapping("/notices")
    public ResponseEntity<ApiResponse<List<Notice>>> getNotices() {
        List<Notice> notices = adminService.getAllNotices();
        return ResponseEntity.ok(ApiResponse.success(notices));
    }

    // ── 공개: 광고 배너 ──

    @GetMapping("/banners")
    public ResponseEntity<ApiResponse<List<AdBanner>>> getActiveBanners() {
        List<AdBanner> banners = adminService.getActiveBanners();
        return ResponseEntity.ok(ApiResponse.success(banners));
    }

    @GetMapping("/application-guides")
    public ResponseEntity<ApiResponse<List<com.najacks.backend.domain.admin.entity.ApplicationGuideImage>>> getApplicationGuides() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getAllGuideImages()));
    }

    // ── 공개: 정적 페이지 (이용약관, 개인정보처리방침, 커뮤니티가이드) ──

    @GetMapping("/pages/{slug}")
    public ResponseEntity<ApiResponse<StaticPage>> getStaticPage(@PathVariable String slug) {
        StaticPage page = adminService.getStaticPage(slug);
        return ResponseEntity.ok(ApiResponse.success(page));
    }
}
