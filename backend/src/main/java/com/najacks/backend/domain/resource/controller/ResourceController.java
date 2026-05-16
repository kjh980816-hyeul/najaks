package com.najacks.backend.domain.resource.controller;

import com.najacks.backend.domain.resource.entity.ResourceLink;
import com.najacks.backend.domain.resource.repository.ResourceLinkRepository;
import com.najacks.backend.global.response.ApiResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceLinkRepository resourceLinkRepository;

    @PostConstruct
    @Transactional
    public void initDefaultResources() {
        if (resourceLinkRepository.count() > 0) return;

        String[][] defaults = {
            {"OBS Studio", "https://obsproject.com", "무료 오픈소스 방송/녹화 프로그램", "방송 도구"},
            {"Streamlabs", "https://streamlabs.com", "방송 알림, 위젯, 오버레이 도구", "방송 도구"},
            {"StreamElements", "https://streamelements.com", "방송 관리 및 커스텀 오버레이", "방송 도구"},
            {"Canva", "https://www.canva.com", "배너, 썸네일, 오버레이 디자인", "디자인/편집"},
            {"DaVinci Resolve", "https://www.blackmagicdesign.com/products/davinciresolve", "무료 영상 편집 프로그램", "디자인/편집"},
            {"Figma", "https://www.figma.com", "오버레이, 패널 디자인 제작", "디자인/편집"},
            {"Remove.bg", "https://www.remove.bg", "이미지 배경 자동 제거", "디자인/편집"},
            {"Pixabay Music", "https://pixabay.com/music", "무료 저작권 프리 음악", "음악/효과음"},
            {"Epidemic Sound", "https://www.epidemicsound.com", "유료 고품질 BGM 라이브러리", "음악/효과음"},
            {"Myinstants", "https://www.myinstants.com", "효과음 버튼 모음", "음악/효과음"},
            {"Discord", "https://discord.com", "커뮤니티 서버 운영", "채팅/커뮤니티"},
            {"Nightbot", "https://nightbot.tv", "채팅 봇, 명령어, 타이머", "채팅/커뮤니티"},
            {"BTTV", "https://betterttv.com", "채팅 이모티콘 확장", "채팅/커뮤니티"},
            {"Social Blade", "https://socialblade.com", "채널 통계 및 성장 분석", "수익화/분석"},
            {"TwitchTracker", "https://twitchtracker.com", "방송 통계 및 트렌드", "수익화/분석"},
            {"Ko-fi", "https://ko-fi.com", "후원/도네이션 페이지 생성", "수익화/분석"},
        };

        for (String[] d : defaults) {
            resourceLinkRepository.save(ResourceLink.builder()
                    .name(d[0]).url(d[1]).description(d[2]).category(d[3]).build());
        }
    }

    @GetMapping("/api/public/resources")
    public ResponseEntity<ApiResponse<List<ResourceLink>>> getResources() {
        List<ResourceLink> resources = resourceLinkRepository.findAllByOrderByCategoryAscCreatedAtDesc();
        return ResponseEntity.ok(ApiResponse.success(resources));
    }

    @PostMapping("/api/admin/resources")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ResourceLink>> createResource(@RequestBody Map<String, String> body) {
        ResourceLink resource = ResourceLink.builder()
                .name(body.get("name"))
                .url(body.get("url"))
                .description(body.get("description"))
                .category(body.get("category"))
                .build();
        ResourceLink saved = resourceLinkRepository.save(resource);
        return ResponseEntity.ok(ApiResponse.success("리소스가 추가되었습니다", saved));
    }

    @DeleteMapping("/api/admin/resources/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteResource(@PathVariable Long id) {
        resourceLinkRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("리소스가 삭제되었습니다", null));
    }
}
