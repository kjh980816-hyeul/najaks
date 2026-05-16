package com.najacks.backend.domain.report.controller;

import com.najacks.backend.domain.report.dto.ReportRequest;
import com.najacks.backend.domain.report.service.ReportService;
import com.najacks.backend.global.response.ApiResponse;
import com.najacks.backend.global.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/reports")
    public ResponseEntity<ApiResponse<Void>> createReport(@Valid @RequestBody ReportRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        reportService.createReport(userId, request);
        return ResponseEntity.ok(ApiResponse.success("신고가 접수되었습니다", null));
    }

    @PostMapping("/users/{id}/block")
    public ResponseEntity<ApiResponse<Void>> blockUser(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        reportService.blockUser(userId, id);
        return ResponseEntity.ok(ApiResponse.success("사용자를 차단했습니다", null));
    }

    @DeleteMapping("/users/{id}/block")
    public ResponseEntity<ApiResponse<Void>> unblockUser(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        reportService.unblockUser(userId, id);
        return ResponseEntity.ok(ApiResponse.success("차단을 해제했습니다", null));
    }

    @GetMapping("/users/me/blocked")
    public ResponseEntity<ApiResponse<List<Long>>> getBlockedUsers() {
        Long userId = SecurityUtil.getCurrentUserId();
        List<Long> blockedIds = reportService.getBlockedUserIds(userId);
        return ResponseEntity.ok(ApiResponse.success(blockedIds));
    }
}
