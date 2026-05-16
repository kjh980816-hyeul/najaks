package com.najacks.backend.domain.user.controller;

import com.najacks.backend.domain.user.dto.*;
import com.najacks.backend.domain.user.service.StreamerService;
import com.najacks.backend.global.response.ApiResponse;
import com.najacks.backend.global.util.SecurityUtil;
import com.najacks.backend.infra.s3.S3Service;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class StreamerController {

    private final StreamerService streamerService;
    private final S3Service s3Service;

    // ── 공개 API ──

    @GetMapping("/api/public/streamers")
    public ResponseEntity<ApiResponse<List<StreamerProfileDto>>> getStreamers() {
        List<StreamerProfileDto> streamers = streamerService.getVerifiedStreamers();
        return ResponseEntity.ok(ApiResponse.success(streamers));
    }

    @GetMapping("/api/public/streamers/{userId}")
    public ResponseEntity<ApiResponse<StreamerProfileDto>> getStreamerProfile(@PathVariable Long userId) {
        StreamerProfileDto profile = streamerService.getStreamerProfile(userId);
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    // ── 인증 필요 API ──

    @PutMapping("/api/streamers/profile")
    public ResponseEntity<ApiResponse<StreamerProfileDto>> updateProfile(
            @RequestBody StreamerProfileUpdateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        StreamerProfileDto profile = streamerService.updateStreamerProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success("프로필이 업데이트되었습니다", profile));
    }

    // ── 스트리머 인증 신청 ──

    @PostMapping(value = "/api/streamer-applications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<StreamerApplicationResponse>> apply(
            @RequestParam("screenshots") List<MultipartFile> screenshots) {
        if (screenshots == null || screenshots.isEmpty()) {
            throw new IllegalArgumentException("스크린샷이 1장 이상 필요합니다");
        }
        Long userId = SecurityUtil.getCurrentUserId();
        List<String> urls = new java.util.ArrayList<>();
        for (MultipartFile file : screenshots) {
            if (file == null || file.isEmpty()) continue;
            urls.add(s3Service.upload(file, "streamer-applications"));
        }
        if (urls.isEmpty()) {
            throw new IllegalArgumentException("유효한 스크린샷 파일이 없습니다");
        }
        StreamerApplicationRequest request = new StreamerApplicationRequest(urls);
        StreamerApplicationResponse response = streamerService.applyForStreamer(userId, request);
        return ResponseEntity.ok(ApiResponse.success("스트리머 인증 신청이 완료되었습니다", response));
    }

    @GetMapping("/api/streamer-applications/me")
    public ResponseEntity<ApiResponse<StreamerApplicationResponse>> getMyApplication() {
        Long userId = SecurityUtil.getCurrentUserId();
        StreamerApplicationResponse response = streamerService.getMyApplication(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ── 파일 업로드 ──

    @PostMapping(value = "/api/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<String>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "directory", defaultValue = "general") String directory) {
        String url = s3Service.upload(file, directory);
        return ResponseEntity.ok(ApiResponse.success("파일이 업로드되었습니다", url));
    }
}
