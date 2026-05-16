package com.najacks.backend.domain.broadcast.controller;

import com.najacks.backend.domain.broadcast.dto.BroadcastScheduleRequest;
import com.najacks.backend.domain.broadcast.dto.BroadcastScheduleResponse;
import com.najacks.backend.domain.broadcast.service.BroadcastService;
import com.najacks.backend.global.response.ApiResponse;
import com.najacks.backend.global.util.SecurityUtil;
import com.najacks.backend.infra.s3.S3Service;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class BroadcastController {

    private final BroadcastService broadcastService;

    @Autowired(required = false)
    private S3Service s3Service;

    @GetMapping("/api/public/schedules")
    public ResponseEntity<ApiResponse<List<BroadcastScheduleResponse>>> getTodaySchedules() {
        List<BroadcastScheduleResponse> schedules = broadcastService.getTodaySchedules();
        return ResponseEntity.ok(ApiResponse.success(schedules));
    }

    @GetMapping("/api/public/streamers/{streamerId}/schedules")
    public ResponseEntity<ApiResponse<List<BroadcastScheduleResponse>>> getStreamerSchedules(
            @PathVariable Long streamerId) {
        List<BroadcastScheduleResponse> schedules = broadcastService.getStreamerSchedules(streamerId);
        return ResponseEntity.ok(ApiResponse.success(schedules));
    }

    @PostMapping(value = "/api/schedules", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BroadcastScheduleResponse>> createSchedule(
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam String scheduledAt,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        Long userId = SecurityUtil.getCurrentUserId();

        String imageUrl = null;
        if (image != null && !image.isEmpty() && s3Service != null) {
            imageUrl = s3Service.upload(image, "schedule-images");
        }

        BroadcastScheduleRequest request = new BroadcastScheduleRequest(title, description, LocalDateTime.parse(scheduledAt));
        BroadcastScheduleResponse response = broadcastService.createSchedule(userId, request, imageUrl);
        return ResponseEntity.ok(ApiResponse.success("방송 예고가 등록되었습니다", response));
    }

    @DeleteMapping("/api/schedules/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSchedule(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        broadcastService.deleteSchedule(id, userId);
        return ResponseEntity.ok(ApiResponse.success("방송 예고가 삭제되었습니다", null));
    }
}
