package com.najacks.backend.domain.admin.controller;

import com.najacks.backend.domain.admin.entity.AdBanner;
import com.najacks.backend.domain.admin.entity.ApplicationGuideImage;
import com.najacks.backend.domain.admin.entity.Notice;
import com.najacks.backend.domain.admin.entity.StaticPage;
import com.najacks.backend.domain.admin.entity.StaticPageHistory;
import com.najacks.backend.domain.admin.service.AdminService;
import com.najacks.backend.domain.user.entity.User;
import com.najacks.backend.domain.content.dto.ContentResponse;
import com.najacks.backend.domain.content.service.ContentService;
import com.najacks.backend.domain.report.dto.ReportProcessRequest;
import com.najacks.backend.domain.report.dto.ReportResponse;
import com.najacks.backend.domain.report.entity.ReportStatus;
import com.najacks.backend.domain.report.service.ReportService;
import com.najacks.backend.domain.stream.service.StreamerNotionSyncService;
import com.najacks.backend.domain.user.dto.StreamerApplicationResponse;
import com.najacks.backend.domain.user.service.StreamerService;
import com.najacks.backend.global.response.ApiResponse;
import com.najacks.backend.global.util.SecurityUtil;
import com.najacks.backend.infra.s3.S3Service;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final StreamerService streamerService;
    private final ContentService contentService;
    private final AdminService adminService;
    private final ReportService reportService;
    private final S3Service s3Service;
    private final StreamerNotionSyncService streamerNotionSync;

    @GetMapping("/streamer-applications")
    public ResponseEntity<ApiResponse<List<StreamerApplicationResponse>>> getAllApplications() {
        List<StreamerApplicationResponse> applications = streamerService.getAllApplications();
        return ResponseEntity.ok(ApiResponse.success(applications));
    }

    @GetMapping("/streamer-applications/pending")
    public ResponseEntity<ApiResponse<List<StreamerApplicationResponse>>> getPendingApplications() {
        List<StreamerApplicationResponse> applications = streamerService.getPendingApplications();
        return ResponseEntity.ok(ApiResponse.success(applications));
    }

    @PostMapping("/streamer-applications/{id}/approve")
    public ResponseEntity<ApiResponse<StreamerApplicationResponse>> approve(@PathVariable Long id) {
        Long adminId = SecurityUtil.getCurrentUserId();
        StreamerApplicationResponse response = streamerService.approveApplication(id, adminId);
        return ResponseEntity.ok(ApiResponse.success("스트리머 인증이 승인되었습니다", response));
    }

    @PostMapping("/streamer-applications/{id}/reject")
    public ResponseEntity<ApiResponse<StreamerApplicationResponse>> reject(
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        Long adminId = SecurityUtil.getCurrentUserId();
        String reason = body != null ? body.get("reason") : null;
        StreamerApplicationResponse response = streamerService.rejectApplication(id, adminId, reason);
        return ResponseEntity.ok(ApiResponse.success("스트리머 인증이 반려되었습니다", response));
    }

    // ── 컨텐츠 승인 관리 ──

    @GetMapping("/contents")
    public ResponseEntity<ApiResponse<List<ContentResponse>>> getAllContents() {
        List<ContentResponse> contents = contentService.getAllContents();
        return ResponseEntity.ok(ApiResponse.success(contents));
    }

    @GetMapping("/contents/pending")
    public ResponseEntity<ApiResponse<List<ContentResponse>>> getPendingContents() {
        List<ContentResponse> contents = contentService.getPendingContents();
        return ResponseEntity.ok(ApiResponse.success(contents));
    }

    @PostMapping("/contents/{id}/approve")
    public ResponseEntity<ApiResponse<ContentResponse>> approveContent(@PathVariable Long id) {
        ContentResponse response = contentService.approveContent(id);
        return ResponseEntity.ok(ApiResponse.success("컨텐츠가 승인되었습니다", response));
    }

    @PostMapping("/contents/{id}/reject")
    public ResponseEntity<ApiResponse<ContentResponse>> rejectContent(@PathVariable Long id) {
        ContentResponse response = contentService.rejectContent(id);
        return ResponseEntity.ok(ApiResponse.success("컨텐츠가 반려되었습니다", response));
    }

    @DeleteMapping("/contents/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteContent(@PathVariable Long id) {
        contentService.deleteContent(id);
        return ResponseEntity.ok(ApiResponse.success("컨텐츠가 삭제되었습니다", null));
    }

    // ── 공지사항 관리 ──

    @PostMapping("/notices")
    public ResponseEntity<ApiResponse<Notice>> createNotice(
            @RequestParam String title,
            @RequestParam String content,
            @RequestParam(defaultValue = "false") boolean pinned) {
        Long adminId = SecurityUtil.getCurrentUserId();
        Notice notice = adminService.createNotice(adminId, title, content, pinned);
        return ResponseEntity.ok(ApiResponse.success("공지사항이 등록되었습니다", notice));
    }

    @DeleteMapping("/notices/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNotice(@PathVariable Long id) {
        adminService.deleteNotice(id);
        return ResponseEntity.ok(ApiResponse.success("공지사항이 삭제되었습니다", null));
    }

    // ── 광고 배너 관리 ──

    @GetMapping("/banners")
    public ResponseEntity<ApiResponse<List<AdBanner>>> getAllBanners() {
        List<AdBanner> banners = adminService.getAllBanners();
        return ResponseEntity.ok(ApiResponse.success(banners));
    }

    @PostMapping(value = "/banners", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AdBanner>> createBanner(
            @RequestParam String title,
            @RequestParam String linkUrl,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "imageUrl", required = false) String imageUrlParam) {
        String imageUrl;
        if (image != null && !image.isEmpty() && s3Service != null) {
            imageUrl = s3Service.upload(image, "ad-banners");
        } else if (imageUrlParam != null && !imageUrlParam.isBlank()) {
            imageUrl = imageUrlParam;
        } else {
            imageUrl = "";
        }
        AdBanner banner = adminService.createBanner(title, imageUrl, linkUrl,
                LocalDateTime.parse(startDate), LocalDateTime.parse(endDate));
        return ResponseEntity.ok(ApiResponse.success("배너가 등록되었습니다", banner));
    }

    @PostMapping("/banners/recompress")
    public ResponseEntity<ApiResponse<String>> recompressBanners() {
        List<AdBanner> banners = adminService.getAllBanners();
        int count = 0;
        for (AdBanner banner : banners) {
            String oldUrl = banner.getImageUrl();
            if (oldUrl != null && oldUrl.startsWith("/uploads/")) {
                String newUrl = s3Service.recompressLocal(oldUrl);
                if (!newUrl.equals(oldUrl)) {
                    banner.updateImageUrl(newUrl);
                    adminService.saveBanner(banner);
                    count++;
                }
            }
        }
        return ResponseEntity.ok(ApiResponse.success(count + "개 배너 이미지 압축 완료", null));
    }

    @PutMapping(value = "/banners/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AdBanner>> updateBanner(
            @PathVariable Long id,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String linkUrl,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Boolean active,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "imageUrl", required = false) String imageUrlParam) {
        String imageUrl = null;
        if (image != null && !image.isEmpty() && s3Service != null) {
            imageUrl = s3Service.upload(image, "ad-banners");
        } else if (imageUrlParam != null && !imageUrlParam.isBlank()) {
            imageUrl = imageUrlParam;
        }
        LocalDateTime sd = (startDate != null && !startDate.isBlank()) ? LocalDateTime.parse(startDate) : null;
        LocalDateTime ed = (endDate != null && !endDate.isBlank()) ? LocalDateTime.parse(endDate) : null;
        AdBanner banner = adminService.updateBanner(id, title, linkUrl, sd, ed, active, imageUrl);
        return ResponseEntity.ok(ApiResponse.success("배너가 수정되었습니다", banner));
    }

    @DeleteMapping("/banners/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBanner(@PathVariable Long id) {
        adminService.deleteBanner(id);
        return ResponseEntity.ok(ApiResponse.success("배너가 삭제되었습니다", null));
    }

    // ── 회원 관리 ──

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<User>>> getAllUsers(
            @RequestParam(required = false) com.najacks.backend.domain.user.entity.Role role) {
        List<User> users = adminService.getAllUsers();
        if (role != null) {
            users = users.stream().filter(u -> u.getRole() == role).toList();
        }
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @GetMapping("/users/search")
    public ResponseEntity<ApiResponse<List<User>>> searchUsers(@RequestParam String q) {
        List<User> users = adminService.searchUsers(q);
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        adminService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success("회원이 삭제되었습니다", null));
    }

    // ── 스트리머 신청 가이드 이미지 ──

    @GetMapping("/application-guides")
    public ResponseEntity<ApiResponse<List<ApplicationGuideImage>>> getGuideImages() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getAllGuideImages()));
    }

    @PostMapping(value = "/application-guides/{platform}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ApplicationGuideImage>> uploadGuideImage(
            @PathVariable String platform,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "imageUrl", required = false) String imageUrlParam,
            @RequestParam(value = "description", required = false) String description) {
        String imageUrl;
        if (image != null && !image.isEmpty()) {
            imageUrl = s3Service.upload(image, "application-guides");
        } else if (imageUrlParam != null && !imageUrlParam.isBlank()) {
            imageUrl = imageUrlParam;
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.success("이미지 파일 또는 URL을 입력해주세요", null));
        }
        ApplicationGuideImage guide = adminService.upsertGuideImage(platform, imageUrl, description);
        return ResponseEntity.ok(ApiResponse.success("가이드 이미지가 저장되었습니다", guide));
    }

    @DeleteMapping("/application-guides/{platform}")
    public ResponseEntity<ApiResponse<Void>> deleteGuideImage(@PathVariable String platform) {
        adminService.deleteGuideImage(platform);
        return ResponseEntity.ok(ApiResponse.success("가이드 이미지가 삭제되었습니다", null));
    }

    // ── 정적 페이지 관리 ──

    @GetMapping("/pages")
    public ResponseEntity<ApiResponse<List<StaticPage>>> getAllStaticPages() {
        List<StaticPage> pages = adminService.getAllStaticPages();
        return ResponseEntity.ok(ApiResponse.success(pages));
    }

    @PutMapping("/pages/{slug}")
    public ResponseEntity<ApiResponse<StaticPage>> updateStaticPage(
            @PathVariable String slug,
            @RequestBody java.util.Map<String, String> body) {
        Long adminId = SecurityUtil.getCurrentUserId();
        StaticPage page = adminService.updateStaticPage(adminId, slug, body.get("title"), body.get("content"));
        return ResponseEntity.ok(ApiResponse.success("페이지가 수정되었습니다", page));
    }

    @GetMapping("/pages/{slug}/history")
    public ResponseEntity<ApiResponse<List<StaticPageHistory>>> getStaticPageHistory(
            @PathVariable String slug) {
        List<StaticPageHistory> history = adminService.getStaticPageHistory(slug);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @GetMapping("/pages/{slug}/history/{version}")
    public ResponseEntity<ApiResponse<StaticPageHistory>> getStaticPageVersion(
            @PathVariable String slug,
            @PathVariable Integer version) {
        StaticPageHistory entry = adminService.getStaticPageVersion(slug, version);
        return ResponseEntity.ok(ApiResponse.success(entry));
    }

    @PostMapping("/pages/{slug}/restore/{version}")
    public ResponseEntity<ApiResponse<StaticPage>> restoreStaticPage(
            @PathVariable String slug,
            @PathVariable Integer version) {
        Long adminId = SecurityUtil.getCurrentUserId();
        StaticPage page = adminService.restoreStaticPage(adminId, slug, version);
        return ResponseEntity.ok(ApiResponse.success("이전 버전으로 복구되었습니다", page));
    }

    // ── 신고 관리 ──

    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<List<ReportResponse>>> getReports(
            @RequestParam(required = false) ReportStatus status) {
        List<ReportResponse> reports = reportService.getReportsForAdmin(status);
        return ResponseEntity.ok(ApiResponse.success(reports));
    }

    @PostMapping("/reports/{id}/process")
    public ResponseEntity<ApiResponse<ReportResponse>> processReport(
            @PathVariable Long id,
            @Valid @RequestBody ReportProcessRequest request) {
        Long adminId = SecurityUtil.getCurrentUserId();
        ReportResponse response = reportService.processReport(id, request.action(), adminId);
        return ResponseEntity.ok(ApiResponse.success("신고가 처리되었습니다", response));
    }

    // ── 스트리머 Notion 일괄 동기화 ──

    @PostMapping("/streamers/sync-notion")
    public ResponseEntity<ApiResponse<java.util.Map<String, Integer>>> syncStreamersNotion() {
        java.util.Map<String, Integer> result = streamerNotionSync.syncAllStreamers();
        return ResponseEntity.ok(ApiResponse.success("스트리머 Notion 동기화 완료", result));
    }
}
