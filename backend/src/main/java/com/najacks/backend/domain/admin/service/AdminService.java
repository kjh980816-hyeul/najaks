package com.najacks.backend.domain.admin.service;

import com.najacks.backend.domain.admin.entity.AdBanner;
import com.najacks.backend.domain.admin.entity.ApplicationGuideImage;
import com.najacks.backend.domain.admin.entity.Notice;
import com.najacks.backend.domain.admin.entity.StaticPage;
import com.najacks.backend.domain.admin.entity.StaticPageHistory;
import com.najacks.backend.domain.admin.repository.AdBannerRepository;
import com.najacks.backend.domain.admin.repository.ApplicationGuideImageRepository;
import com.najacks.backend.domain.admin.repository.NoticeRepository;
import com.najacks.backend.domain.admin.repository.StaticPageHistoryRepository;
import com.najacks.backend.domain.admin.repository.StaticPageRepository;
import com.najacks.backend.domain.user.entity.Role;
import com.najacks.backend.domain.user.entity.User;
import com.najacks.backend.domain.user.repository.StreamerApplicationRepository;
import com.najacks.backend.domain.user.repository.StreamerProfileRepository;
import com.najacks.backend.domain.user.repository.UserRepository;
import com.najacks.backend.global.exception.CustomException;
import com.najacks.backend.global.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final EntityManager entityManager;
    private final NoticeRepository noticeRepository;
    private final AdBannerRepository adBannerRepository;
    private final StaticPageRepository staticPageRepository;
    private final StaticPageHistoryRepository staticPageHistoryRepository;
    private final ApplicationGuideImageRepository applicationGuideImageRepository;
    private final StreamerProfileRepository streamerProfileRepository;
    private final StreamerApplicationRepository streamerApplicationRepository;
    private final UserRepository userRepository;

    // ── 공지사항 ──

    @Transactional(readOnly = true)
    public List<Notice> getAllNotices() {
        return noticeRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Notice> getPinnedNotices() {
        return noticeRepository.findByPinnedTrue();
    }

    @Transactional
    public Notice createNotice(Long authorId, String title, String content, boolean pinned) {
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Notice notice = Notice.builder()
                .author(author)
                .title(title)
                .content(content)
                .pinned(pinned)
                .build();

        return noticeRepository.save(notice);
    }

    @Transactional
    public void deleteNotice(Long noticeId) {
        noticeRepository.deleteById(noticeId);
    }

    // ── 광고 배너 ──

    @Transactional(readOnly = true)
    public List<AdBanner> getActiveBanners() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        return adBannerRepository.findByActiveTrueAndStartDateBeforeAndEndDateAfter(now, now);
    }

    @Transactional(readOnly = true)
    public List<AdBanner> getAllBanners() {
        return adBannerRepository.findAll();
    }

    @Transactional
    public AdBanner createBanner(String title, String imageUrl, String linkUrl,
                                  LocalDateTime startDate, LocalDateTime endDate) {
        AdBanner banner = AdBanner.builder()
                .title(title)
                .imageUrl(imageUrl)
                .linkUrl(linkUrl)
                .startDate(startDate)
                .endDate(endDate)
                .active(true)
                .clickCount(0)
                .build();

        return adBannerRepository.save(banner);
    }

    @Transactional
    public AdBanner saveBanner(AdBanner banner) {
        return adBannerRepository.save(banner);
    }

    @Transactional
    public AdBanner updateBanner(Long bannerId, String title, String linkUrl,
                                  LocalDateTime startDate, LocalDateTime endDate,
                                  Boolean active, String imageUrl) {
        AdBanner banner = adBannerRepository.findById(bannerId)
                .orElseThrow(() -> new CustomException(ErrorCode.BANNER_NOT_FOUND));
        banner.update(title, linkUrl, startDate, endDate, active, imageUrl);
        return adBannerRepository.save(banner);
    }

    @Transactional
    public void deleteBanner(Long bannerId) {
        adBannerRepository.deleteById(bannerId);
    }

    // ── 스트리머 신청 가이드 이미지 ──

    @Transactional(readOnly = true)
    public List<ApplicationGuideImage> getAllGuideImages() {
        return applicationGuideImageRepository.findAll();
    }

    @Transactional
    public ApplicationGuideImage upsertGuideImage(String platform, String imageUrl, String description) {
        return applicationGuideImageRepository.findByPlatform(platform)
                .map(existing -> {
                    existing.update(imageUrl, description);
                    return applicationGuideImageRepository.save(existing);
                })
                .orElseGet(() -> applicationGuideImageRepository.save(
                        ApplicationGuideImage.builder()
                                .platform(platform)
                                .imageUrl(imageUrl)
                                .description(description)
                                .build()
                ));
    }

    @Transactional
    public void deleteGuideImage(String platform) {
        applicationGuideImageRepository.deleteByPlatform(platform);
    }

    // ── 회원 관리 ──

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<User> searchUsers(String query) {
        String q = "%" + query.toLowerCase() + "%";
        return userRepository.findByNicknameLikeOrEmailLike(q, q);
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (user.getRole() == Role.ADMIN) {
            throw new IllegalArgumentException("관리자 계정은 삭제할 수 없습니다");
        }

        // 연관 데이터 삭제 (native query로 처리)
        entityManager.createNativeQuery("DELETE FROM post_likes WHERE user_id = :uid").setParameter("uid", userId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM comments WHERE author_id = :uid").setParameter("uid", userId).executeUpdate();
        entityManager.createNativeQuery("UPDATE reports SET processed_by = NULL WHERE processed_by = :uid").setParameter("uid", userId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM reports WHERE reporter_id = :uid").setParameter("uid", userId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM notifications WHERE user_id = :uid").setParameter("uid", userId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM blocks WHERE blocker_id = :uid OR blocked_id = :uid").setParameter("uid", userId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM clips WHERE streamer_id = :uid").setParameter("uid", userId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM broadcast_schedules WHERE streamer_id = :uid").setParameter("uid", userId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM contents WHERE streamer_id = :uid").setParameter("uid", userId).executeUpdate();
        // 게시글 삭제 전 해당 게시글의 좋아요/댓글 먼저 삭제
        entityManager.createNativeQuery("DELETE FROM post_likes WHERE post_id IN (SELECT id FROM posts WHERE author_id = :uid)").setParameter("uid", userId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM comments WHERE post_id IN (SELECT id FROM posts WHERE author_id = :uid)").setParameter("uid", userId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM posts WHERE author_id = :uid").setParameter("uid", userId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM notices WHERE author_id = :uid").setParameter("uid", userId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM refresh_tokens WHERE user_id = :uid").setParameter("uid", userId).executeUpdate();
        entityManager.createNativeQuery("UPDATE streamer_applications SET reviewed_by = NULL WHERE reviewed_by = :uid").setParameter("uid", userId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM streamer_application_screenshots WHERE application_id IN (SELECT id FROM streamer_applications WHERE user_id = :uid)").setParameter("uid", userId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM streamer_applications WHERE user_id = :uid").setParameter("uid", userId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM streamer_profiles WHERE user_id = :uid").setParameter("uid", userId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM users WHERE id = :uid").setParameter("uid", userId).executeUpdate();
    }

    // ── 정적 페이지 (이용약관, 개인정보처리방침, 커뮤니티가이드) ──

    @Transactional(readOnly = true)
    public StaticPage getStaticPage(String slug) {
        return staticPageRepository.findBySlug(slug)
                .orElseThrow(() -> new CustomException(ErrorCode.PAGE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<StaticPage> getAllStaticPages() {
        return staticPageRepository.findAll();
    }

    @Transactional
    public StaticPage updateStaticPage(Long adminId, String slug, String title, String content) {
        StaticPage page = staticPageRepository.findBySlug(slug)
                .orElseThrow(() -> new CustomException(ErrorCode.PAGE_NOT_FOUND));
        page.update(title, content);
        staticPageRepository.save(page);
        recordHistory(slug, title, content, adminId);
        return page;
    }

    @Transactional(readOnly = true)
    public List<StaticPageHistory> getStaticPageHistory(String slug) {
        staticPageRepository.findBySlug(slug)
                .orElseThrow(() -> new CustomException(ErrorCode.PAGE_NOT_FOUND));
        return staticPageHistoryRepository.findBySlugOrderByVersionDesc(slug);
    }

    @Transactional(readOnly = true)
    public StaticPageHistory getStaticPageVersion(String slug, Integer version) {
        return staticPageHistoryRepository.findBySlugAndVersion(slug, version)
                .orElseThrow(() -> new CustomException(ErrorCode.PAGE_NOT_FOUND));
    }

    @Transactional
    public StaticPage restoreStaticPage(Long adminId, String slug, Integer version) {
        StaticPageHistory target = staticPageHistoryRepository.findBySlugAndVersion(slug, version)
                .orElseThrow(() -> new CustomException(ErrorCode.PAGE_NOT_FOUND));
        return updateStaticPage(adminId, slug, target.getTitle(), target.getContent());
    }

    private void recordHistory(String slug, String title, String content, Long adminId) {
        int nextVersion = staticPageHistoryRepository.findTopBySlugOrderByVersionDesc(slug)
                .map(h -> h.getVersion() + 1)
                .orElse(1);
        String email = null;
        if (adminId != null) {
            email = userRepository.findById(adminId).map(User::getEmail).orElse(null);
        }
        staticPageHistoryRepository.save(StaticPageHistory.builder()
                .slug(slug)
                .version(nextVersion)
                .title(title)
                .content(content)
                .editedById(adminId)
                .editedByEmail(email)
                .build());
    }

    private void upsertStaticPage(String slug, String title, String content) {
        staticPageRepository.findBySlug(slug).ifPresentOrElse(
                existing -> {
                    // 초기 테스트 스텁(100자 미만) 또는 옛 플레이스홀더 이메일이 남은 경우 덮어씀
                    String cur = existing.getContent();
                    boolean isStub = cur == null || cur.length() < 100;
                    boolean hasLegacyEmail = cur != null
                            && (cur.contains("najaks.local") || cur.contains("kjh980816@gmail.com"));
                    if (isStub || hasLegacyEmail) {
                        existing.update(title, content);
                        staticPageRepository.save(existing);
                    }
                    // 기존 페이지에 히스토리가 없으면 현재 상태를 v1으로 스냅샷
                    if (!staticPageHistoryRepository.existsBySlug(slug)) {
                        recordHistory(slug, existing.getTitle(), existing.getContent(), null);
                    }
                },
                () -> {
                    staticPageRepository.save(StaticPage.builder()
                            .slug(slug)
                            .title(title)
                            .content(content)
                            .build());
                    recordHistory(slug, title, content, null);
                }
        );
    }

    @PostConstruct
    @Transactional
    public void initStaticPages() {
        upsertStaticPage("terms", "이용약관",
                "[{\"heading\":\"제1조 (목적)\",\"body\":\"이 약관은 NAJAKS(이하 \\\"서비스\\\")가 제공하는 스트리머 커뮤니티 플랫폼의 이용 조건 및 절차, 회원과 서비스의 권리·의무·책임 사항 등을 규정함을 목적으로 합니다.\"},{\"heading\":\"제2조 (정의)\",\"body\":\"\\\"회원\\\"이란 본 약관에 동의하고 서비스에 가입한 자를 말하며, 시청자(FAN), 스트리머(STREAMER), 관리자(ADMIN)로 구분됩니다.\\n\\\"스트리머\\\"란 별도 인증 절차를 거쳐 승인된 회원을 의미합니다.\\n\\\"콘텐츠\\\"란 서비스 내에서 회원이 게시한 글, 댓글, 이미지, 영상 링크 등 모든 정보를 말합니다.\"},{\"heading\":\"제3조 (회원가입)\",\"body\":\"회원가입은 본 약관 및 개인정보처리방침에 동의한 후 가입 신청을 완료함으로써 성립됩니다.\\n타인의 정보를 도용하거나 허위 정보를 기재한 가입은 무효 처리되며 서비스 이용이 제한될 수 있습니다.\"},{\"heading\":\"제4조 (서비스의 제공 및 변경)\",\"body\":\"서비스는 회원에게 다음과 같은 기능을 제공합니다: 커뮤니티 게시판, 콘텐츠 홍보, 클립 공유, 방송 일정, 알림, 신고/차단 등.\\n서비스는 운영상·기술상 필요에 따라 제공 기능의 일부 또는 전부를 변경할 수 있습니다.\"},{\"heading\":\"제5조 (회원의 의무)\",\"body\":\"회원은 다음 행위를 하여서는 안 됩니다.\\n- 타인의 정보 도용\\n- 음란물, 폭력적·차별적 콘텐츠 게시\\n- 무단 광고·스팸 게시\\n- 서비스의 정상적 운영을 방해하는 행위\\n- 저작권 등 타인의 권리를 침해하는 행위\"},{\"heading\":\"제6조 (게시물의 관리)\",\"body\":\"서비스는 회원이 게시한 콘텐츠가 본 약관에 위배되거나 신고가 일정 수 이상 누적된 경우 사전 통지 없이 숨김·삭제 처리할 수 있습니다.\"},{\"heading\":\"제7조 (계정 정지 및 해지)\",\"body\":\"회원이 본 약관을 위반한 경우 서비스는 경고, 일시 정지, 영구 정지, 계정 삭제 등의 조치를 취할 수 있습니다.\"},{\"heading\":\"제8조 (면책)\",\"body\":\"서비스는 천재지변, 불가항력, 회원의 귀책 사유로 발생한 손해에 대하여 책임지지 않습니다.\"}]"
        );

        upsertStaticPage("privacy", "개인정보처리방침",
                "[{\"heading\":\"1. 수집하는 개인정보 항목\",\"body\":\"필수: 이메일, 닉네임, 비밀번호(암호화 저장)\\n선택: 프로필 이미지, 자기소개, 방송 시간표, 외부 채널 URL\\n자동 수집: 접속 IP, 쿠키, 서비스 이용 기록\"},{\"heading\":\"2. 개인정보 수집 및 이용 목적\",\"body\":\"- 회원 식별 및 로그인 처리\\n- 서비스 제공 및 콘텐츠 표시\\n- 신고 처리, 분쟁 해결, 부정 이용 방지\\n- 알림 발송 (서비스 공지, 댓글/승인/신고 처리 결과)\"},{\"heading\":\"3. 개인정보 보유 및 이용 기간\",\"body\":\"회원 탈퇴 시 즉시 파기합니다. 다만 관계 법령에 따라 보존이 필요한 경우 해당 기간 동안 보관 후 파기합니다.\\n- 신고 기록: 1년\\n- 접속 로그: 3개월\"},{\"heading\":\"4. 개인정보의 제3자 제공\",\"body\":\"서비스는 회원의 개인정보를 원칙적으로 외부에 제공하지 않습니다. 법령에 의한 요청, 수사 목적의 영장 등 예외적인 경우에 한해 제공될 수 있습니다.\"},{\"heading\":\"5. 이용자의 권리\",\"body\":\"회원은 언제든지 등록된 개인정보를 조회·수정·삭제할 수 있으며, 회원 탈퇴를 통해 동의를 철회할 수 있습니다.\"},{\"heading\":\"6. 개인정보 보호 책임자\",\"body\":\"문의: Admin.Najaks@gmail.com\"}]"
        );

        upsertStaticPage("community-guide", "커뮤니티 가이드",
                "[{\"heading\":\"우리가 지키는 원칙\",\"body\":\"NAJAKS는 스트리머와 팬이 함께 만드는 공간입니다. 서로를 존중하는 것이 가장 중요합니다.\"},{\"heading\":\"권장하는 행동\",\"body\":\"- 응원과 칭찬, 건설적인 피드백\\n- 출처를 명확히 한 정보 공유\\n- 신고 기능으로 문제 해결\\n- 처음 온 분들에게 친절하게\"},{\"heading\":\"금지하는 행동\",\"body\":\"- 욕설, 비방, 인신공격, 차별 발언\\n- 음란물, 도박, 불법 콘텐츠 게시\\n- 광고/스팸 도배\\n- 타인 사칭, 정보 도용\\n- 스트리머 전용 게시판 내용을 외부에 유출하는 행위\"},{\"heading\":\"위반 시 조치\",\"body\":\"신고가 누적되거나 심각한 위반이 확인될 경우, 게시물 숨김 → 일시 정지 → 영구 정지 순으로 조치됩니다.\"},{\"heading\":\"스트리머 전용 공간\",\"body\":\"인증된 스트리머만 접근 가능한 공간의 내용은 외부 공유가 금지되며, 모든 페이지에 닉네임 워터마크가 적용됩니다.\"}]"
        );

        upsertStaticPage("support", "고객센터",
                "[{\"heading\":\"문의 방법\",\"body\":\"이메일: Admin.Najaks@gmail.com\\n또는 사이트 내 문의 게시판(/community?group=community&board=INQUIRY)에 글을 남겨주시면 확인 후 답변드립니다.\\n운영시간: 평일 10:00 ~ 18:00 (주말/공휴일 휴무)\"},{\"heading\":\"답변 기한\",\"body\":\"문의 접수 후 영업일 기준 1~3일 이내에 답변드립니다.\"},{\"heading\":\"자주 묻는 질문\",\"body\":\"Q. 스트리머 인증은 얼마나 걸리나요?\\nA. 신청 후 영업일 기준 1~3일 정도 소요됩니다.\\n\\nQ. 비밀번호를 잊어버렸어요\\nA. 현재 비밀번호 재설정 기능은 준비 중입니다. 고객센터로 문의해주세요.\\n\\nQ. 부적절한 게시물을 발견했어요\\nA. 게시물 옆 신고 버튼을 사용해주세요. 5회 이상 신고된 게시물은 자동으로 숨김 처리됩니다.\"}]"
        );

        upsertStaticPage("contact", "문의하기",
                "[{\"heading\":\"일반 문의\",\"body\":\"Admin.Najaks@gmail.com\\n서비스 이용, 계정 등 일반 문의는 위 이메일로 보내주세요. 또는 사이트 내 문의 게시판을 이용하실 수 있습니다.\"},{\"heading\":\"제휴/광고 문의\",\"body\":\"Admin.Najaks@gmail.com\\n광고 배너, 제휴 마케팅 등 비즈니스 문의는 위 이메일로 보내주세요.\"},{\"heading\":\"버그 / 개선 제안\",\"body\":\"Admin.Najaks@gmail.com\\n버그 제보, 기능 개선 제안은 위 이메일로 보내주세요. 가능한 한 재현 방법과 함께 보내주시면 도움이 됩니다.\"}]"
        );

        upsertStaticPage("report-info", "신고 안내",
                "[{\"heading\":\"신고 방법\",\"body\":\"게시물·댓글·사용자 옆에 표시된 신고 버튼을 통해 즉시 신고할 수 있습니다.\\n신고 사유를 구체적으로 작성해주시면 처리에 도움이 됩니다.\"},{\"heading\":\"자동 숨김 정책\",\"body\":\"동일 콘텐츠에 대해 5회 이상의 신고가 누적되면 즉시 자동으로 숨김 처리됩니다. 이후 관리자가 검토하여 복구·삭제 여부를 결정합니다.\"},{\"heading\":\"처리 기준\",\"body\":\"- 기각: 위반 사항이 확인되지 않은 경우\\n- 콘텐츠 삭제: 게시물·댓글이 가이드를 위반한 경우\\n- 사용자 정지: 반복 위반 또는 심각한 위반인 경우\"},{\"heading\":\"결과 통보\",\"body\":\"신고 처리가 완료되면 신고자에게 알림으로 결과를 안내합니다.\"},{\"heading\":\"허위 신고\",\"body\":\"악의적인 허위 신고가 반복될 경우 신고자에게도 제재가 가해질 수 있습니다.\"}]"
        );
    }
}
