package com.najacks.backend.domain.clip.controller;

import com.najacks.backend.domain.clip.dto.ClipCreateRequest;
import com.najacks.backend.domain.clip.dto.ClipResponse;
import com.najacks.backend.domain.clip.service.ClipService;
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
public class ClipController {

    private final ClipService clipService;
    private final S3Service s3Service;

    @GetMapping("/api/public/clips")
    public ResponseEntity<ApiResponse<List<ClipResponse>>> getPopularClips() {
        List<ClipResponse> clips = clipService.getPopularClips();
        return ResponseEntity.ok(ApiResponse.success(clips));
    }

    @PostMapping("/api/public/clips/{id}/view")
    public ResponseEntity<ApiResponse<Void>> increaseViewCount(@PathVariable Long id) {
        clipService.increaseViewCount(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/api/public/streamers/{streamerId}/clips")
    public ResponseEntity<ApiResponse<List<ClipResponse>>> getStreamerClips(
            @PathVariable Long streamerId) {
        List<ClipResponse> clips = clipService.getStreamerClips(streamerId);
        return ResponseEntity.ok(ApiResponse.success(clips));
    }

    @PostMapping(value = "/api/clips", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ClipResponse>> createClip(
            @RequestParam("title") String title,
            @RequestParam("url") String url,
            @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail) {

        Long userId = SecurityUtil.getCurrentUserId();

        String thumbnailUrl = null;
        if (thumbnail != null && !thumbnail.isEmpty()) {
            thumbnailUrl = s3Service.upload(thumbnail, "clip-thumbnails");
        }

        ClipCreateRequest request = new ClipCreateRequest(title, url, thumbnailUrl);
        ClipResponse response = clipService.createClip(userId, request);
        return ResponseEntity.ok(ApiResponse.success("클립이 등록되었습니다", response));
    }

    @DeleteMapping("/api/clips/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteClip(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        clipService.deleteClip(id, userId);
        return ResponseEntity.ok(ApiResponse.success("클립이 삭제되었습니다", null));
    }
}
