package com.najacks.backend.domain.content.controller;

import com.najacks.backend.domain.content.dto.ContentCreateRequest;
import com.najacks.backend.domain.content.dto.ContentResponse;
import com.najacks.backend.domain.content.entity.ContentCategory;
import com.najacks.backend.domain.content.service.ContentService;
import com.najacks.backend.global.response.ApiResponse;
import com.najacks.backend.global.util.SecurityUtil;
import com.najacks.backend.infra.s3.S3Service;
import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ContentController {

    private final ContentService contentService;
    private final S3Service s3Service;

    // ── 공개 API ──

    @GetMapping("/api/public/contents")
    public ResponseEntity<ApiResponse<List<ContentResponse>>> getContents(
            @RequestParam(required = false) ContentCategory category) {
        List<ContentResponse> contents;
        if (category != null) {
            contents = contentService.getApprovedContentsByCategory(category);
        } else {
            contents = contentService.getApprovedContents();
        }
        return ResponseEntity.ok(ApiResponse.success(contents));
    }

    @GetMapping("/api/public/contents/{id}")
    public ResponseEntity<ApiResponse<ContentResponse>> getContent(@PathVariable Long id) {
        ContentResponse content = contentService.getContent(id);
        return ResponseEntity.ok(ApiResponse.success(content));
    }

    @GetMapping("/api/public/streamers/{streamerId}/contents")
    public ResponseEntity<ApiResponse<List<ContentResponse>>> getStreamerContents(
            @PathVariable Long streamerId) {
        List<ContentResponse> contents = contentService.getContentsByStreamer(streamerId);
        return ResponseEntity.ok(ApiResponse.success(contents));
    }

    // ── 스트리머: 컨텐츠 관리 ──

    @PostMapping(value = "/api/contents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ContentResponse>> createContent(
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "applyLink", required = false) String applyLink,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam("category") ContentCategory category,
            @RequestParam(value = "tags", required = false) List<ContentCategory> tags,
            @RequestParam(value = "requirements", required = false) String requirements,
            @RequestParam(value = "prize", required = false) String prize,
            @RequestParam(value = "recruitCount", required = false) String recruitCount,
            @RequestParam(value = "followerCount", required = false) Integer followerCount,
            @RequestParam(value = "followerUnlimited", required = false, defaultValue = "false") Boolean followerUnlimited,
            @RequestParam(value = "contactMethod", required = false) String contactMethod,
            @RequestParam(value = "contactInfo", required = false) String contactInfo,
            @RequestParam(value = "hostName", required = false) String hostName,
            @RequestParam(value = "images", required = false) List<MultipartFile> images,
            @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail) {

        Long userId = SecurityUtil.getCurrentUserId();

        List<String> imageUrls = new ArrayList<>();
        if (images != null) {
            for (MultipartFile img : images) {
                if (img != null && !img.isEmpty()) {
                    imageUrls.add(s3Service.upload(img, "content-thumbnails"));
                }
            }
        }
        // 이전 버전 호환: thumbnail 단일 파라미터도 받아서 첫 번째 이미지로 사용
        if (imageUrls.isEmpty() && thumbnail != null && !thumbnail.isEmpty()) {
            imageUrls.add(s3Service.upload(thumbnail, "content-thumbnails"));
        }
        String thumbnailUrl = imageUrls.isEmpty() ? null : imageUrls.get(0);

        ContentCreateRequest request = new ContentCreateRequest(
                title, description, thumbnailUrl, imageUrls, applyLink, startDate, endDate,
                category, tags, requirements, prize, recruitCount, followerCount,
                followerUnlimited, contactMethod, contactInfo, hostName
        );

        ContentResponse response = contentService.createContent(userId, request);
        return ResponseEntity.ok(ApiResponse.success("컨텐츠가 등록되었습니다. 관리자 승인 후 노출됩니다.", response));
    }

    @PutMapping(value = "/api/contents/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ContentResponse>> updateContent(
            @PathVariable Long id,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "applyLink", required = false) String applyLink,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam("category") ContentCategory category,
            @RequestParam(value = "tags", required = false) List<ContentCategory> tags,
            @RequestParam(value = "requirements", required = false) String requirements,
            @RequestParam(value = "prize", required = false) String prize,
            @RequestParam(value = "recruitCount", required = false) String recruitCount,
            @RequestParam(value = "followerCount", required = false) Integer followerCount,
            @RequestParam(value = "followerUnlimited", required = false, defaultValue = "false") Boolean followerUnlimited,
            @RequestParam(value = "contactMethod", required = false) String contactMethod,
            @RequestParam(value = "contactInfo", required = false) String contactInfo,
            @RequestParam(value = "hostName", required = false) String hostName,
            @RequestParam(value = "existingImageUrls", required = false) List<String> existingImageUrls,
            @RequestParam(value = "images", required = false) List<MultipartFile> images) {

        Long userId = SecurityUtil.getCurrentUserId();

        List<String> imageUrls = new ArrayList<>();
        if (existingImageUrls != null) imageUrls.addAll(existingImageUrls);
        if (images != null) {
            for (MultipartFile img : images) {
                if (img != null && !img.isEmpty()) {
                    imageUrls.add(s3Service.upload(img, "content-thumbnails"));
                }
            }
        }
        String thumbnailUrl = imageUrls.isEmpty() ? null : imageUrls.get(0);

        ContentCreateRequest request = new ContentCreateRequest(
                title, description, thumbnailUrl, imageUrls, applyLink, startDate, endDate,
                category, tags, requirements, prize, recruitCount, followerCount,
                followerUnlimited, contactMethod, contactInfo, hostName
        );

        ContentResponse response = contentService.updateContent(id, userId, request);
        return ResponseEntity.ok(ApiResponse.success("컨텐츠가 수정되었습니다", response));
    }

    @GetMapping("/api/contents/my")
    public ResponseEntity<ApiResponse<List<ContentResponse>>> getMyContents() {
        Long userId = SecurityUtil.getCurrentUserId();
        List<ContentResponse> contents = contentService.getMyContents(userId);
        return ResponseEntity.ok(ApiResponse.success(contents));
    }

    @PostMapping("/api/contents/{id}/close")
    public ResponseEntity<ApiResponse<ContentResponse>> closeContent(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        ContentResponse response = contentService.closeContent(id, userId);
        return ResponseEntity.ok(ApiResponse.success("컨텐츠가 마감되었습니다", response));
    }
}
