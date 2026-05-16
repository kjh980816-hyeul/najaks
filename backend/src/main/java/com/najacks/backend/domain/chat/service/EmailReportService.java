package com.najacks.backend.domain.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.najacks.backend.ai.GeminiClient;
import com.najacks.backend.domain.chat.crypto.TokenCryptor;
import com.najacks.backend.domain.chat.entity.EmailReportLog;
import com.najacks.backend.domain.chat.entity.EmailUnsubscribeToken;
import com.najacks.backend.domain.chat.entity.StreamAnalysisReport;
import com.najacks.backend.domain.chat.entity.StreamerPremiumFeature;
import com.najacks.backend.domain.chat.event.AnalysisReportReadyEvent;
import com.najacks.backend.domain.chat.repository.EmailReportLogRepository;
import com.najacks.backend.domain.chat.repository.EmailUnsubscribeTokenRepository;
import com.najacks.backend.domain.chat.repository.StreamAnalysisReportRepository;
import com.najacks.backend.domain.chat.repository.StreamerPremiumFeatureRepository;
import com.najacks.backend.domain.stream.repository.StreamerLiveHistoryRepository;
import com.najacks.backend.domain.user.entity.User;
import com.najacks.backend.domain.user.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailReportService {

    private final StreamerPremiumFeatureRepository premiumRepo;
    private final StreamAnalysisReportRepository analysisRepo;
    private final EmailReportLogRepository logRepo;
    private final EmailUnsubscribeTokenRepository tokenRepo;
    private final UserRepository userRepo;
    private final StreamerLiveHistoryRepository historyRepo;
    private final TokenCryptor tokenCryptor;
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final GeminiClient gemini;
    private final ChatAnalysisNotionSync notionSync;
    private final ObjectMapper om = new ObjectMapper();

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Value("${app.mail.from:${spring.mail.username:}}")
    private String mailFrom;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${app.site.base-url:https://najaks.co.kr}")
    private String siteBaseUrl;

    @Async("aiTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAnalysisReady(AnalysisReportReadyEvent ev) {
        send(ev.reportId(), ev.streamerNo());
    }

    @Transactional
    public void send(Long reportId, Long streamerNo) {
        StreamerPremiumFeature premium = premiumRepo.findById(streamerNo).orElse(null);
        if (premium == null) return;
        if (!Boolean.TRUE.equals(premium.getEmailEnabled())) return;

        StreamAnalysisReport report = analysisRepo.findById(reportId).orElse(null);
        if (report == null) return;

        User streamer = userRepo.findById(streamerNo).orElse(null);
        if (streamer == null) return;

        String toEmail = resolveEmail(premium, streamer);
        if (toEmail == null || toEmail.isBlank()) {
            log.info("이메일 주소 없음 — 발송 스킵 streamerNo={}", streamerNo);
            return;
        }

        String smtpUser = (smtpUsername == null || smtpUsername.isBlank()) ? null : smtpUsername;
        String fromAddr = (mailFrom == null || mailFrom.isBlank()) ? smtpUser : mailFrom;
        if (fromAddr == null) {
            log.warn("MAIL_USERNAME 미설정 — 이메일 발송 불가");
            return;
        }

        try {
            String subject = String.format("[나작스] %s 방송 분석 리포트",
                    report.getStartedAt().format(DateTimeFormatter.ofPattern("M월 d일")));

            String unsubToken = createUnsubscribeToken(streamerNo);
            String intro = generateIntro(streamer, report);

            Map<String, Object> vars = new HashMap<>();
            vars.put("streamerNickname", streamer.getNickname());
            vars.put("streamTitle", resolveStreamTitle(report));
            vars.put("streamPeriod", formatPeriod(report.getStartedAt(), report.getEndedAt()));
            vars.put("durationFormatted", formatDuration(report.getDurationMinutes()));
            vars.put("aiIntro", intro);
            vars.put("peakViewers", report.getPeakViewerCount() != null ? report.getPeakViewerCount().toString() : "-");
            vars.put("analysisMood", safe(report.getAiMood(), ""));
            vars.put("highlightCount", nz(report.getHighlightCount()));
            vars.put("totalChats", nz(report.getTotalChatCount()));
            vars.put("uniqueChatters", nz(report.getUniqueChatterCount()));
            vars.put("aiSummary", safe(report.getAiSummary(), "방송 수고하셨습니다."));
            vars.put("keywordsList", parseKeywords(report.getAiTopKeywords()));
            vars.put("audienceInsight", safe(report.getAiAudienceInsight(), ""));
            vars.put("momentsList", parseMoments(report.getAiHighlightMoments()));
            vars.put("tipsList", parseTips(report.getAiImprovementTips()));
            vars.put("siteUrl", siteBaseUrl);
            vars.put("unsubscribeUrl", siteBaseUrl + "/email/unsubscribe/" + unsubToken);

            Context ctx = new Context();
            ctx.setVariables(vars);
            String html = templateEngine.process("email/streamer-report", ctx);

            // Notion 페이지 본문에 이메일과 동일한 섹션 append (속성만으로는 내용이 누락되던 문제 해결)
            if (report.getNotionPageId() != null) {
                try {
                    List<ChatAnalysisNotionSync.MomentItem> notionMoments = parseMoments(report.getAiHighlightMoments()).stream()
                            .map(m -> new ChatAnalysisNotionSync.MomentItem(m.time(), m.title(), m.detail()))
                            .toList();
                    notionSync.populatePageBody(
                            report.getNotionPageId(),
                            new ChatAnalysisNotionSync.PageBodyParams(
                                    intro,
                                    formatPeriod(report.getStartedAt(), report.getEndedAt()),
                                    formatDuration(report.getDurationMinutes()),
                                    report.getPeakViewerCount() != null ? report.getPeakViewerCount().toString() : null,
                                    report.getTotalChatCount(),
                                    report.getUniqueChatterCount(),
                                    report.getHighlightCount(),
                                    report.getAiMood(),
                                    report.getAiSummary(),
                                    parseKeywords(report.getAiTopKeywords()),
                                    report.getAiAudienceInsight(),
                                    notionMoments,
                                    parseTips(report.getAiImprovementTips())
                            )
                    );
                } catch (Exception ex) {
                    log.warn("Notion 본문 동기화 실패 reportId={}", reportId, ex);
                }
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddr, "나작스 AI 분석");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);

            premium.resetEmailFailure();
            premium.setLastReportSentAt(LocalDateTime.now(SEOUL));
            premiumRepo.save(premium);
            logRepo.save(EmailReportLog.success(streamerNo, reportId, subject));

            // Notion "이메일 발송됨" 체크박스 업데이트
            if (report.getNotionPageId() != null) {
                try { notionSync.markEmailSent(report.getNotionPageId()); }
                catch (Exception ignored) {}
            }
            log.info("✉️ 방송 리포트 이메일 발송 완료 streamerNo={} to={}", streamerNo, maskEmail(toEmail));
        } catch (Exception e) {
            log.warn("이메일 발송 실패 streamerNo={}", streamerNo, e);
            handleFailure(premium, reportId, e);
        }
    }

    /** 이메일 발송 없이 Notion 페이지 본문만 채움. 기존 이메일 받은 건에 대해 Notion 만 sync 용. */
    @Transactional
    public boolean syncNotionBody(Long reportId, Long streamerNo) {
        StreamAnalysisReport report = analysisRepo.findById(reportId).orElse(null);
        if (report == null) return false;
        if (report.getNotionPageId() == null) return false;
        User streamer = userRepo.findById(streamerNo).orElse(null);
        if (streamer == null) return false;

        String intro;
        try { intro = generateIntro(streamer, report); }
        catch (Exception e) {
            intro = String.format("%s님, 오늘도 방송하시느라 수고 많으셨어요.", streamer.getNickname());
        }

        List<ChatAnalysisNotionSync.MomentItem> notionMoments = parseMoments(report.getAiHighlightMoments()).stream()
                .map(m -> new ChatAnalysisNotionSync.MomentItem(m.time(), m.title(), m.detail()))
                .toList();

        notionSync.populatePageBody(
                report.getNotionPageId(),
                new ChatAnalysisNotionSync.PageBodyParams(
                        intro,
                        formatPeriod(report.getStartedAt(), report.getEndedAt()),
                        formatDuration(report.getDurationMinutes()),
                        report.getPeakViewerCount() != null ? report.getPeakViewerCount().toString() : null,
                        report.getTotalChatCount(),
                        report.getUniqueChatterCount(),
                        report.getHighlightCount(),
                        report.getAiMood(),
                        report.getAiSummary(),
                        parseKeywords(report.getAiTopKeywords()),
                        report.getAiAudienceInsight(),
                        notionMoments,
                        parseTips(report.getAiImprovementTips())
                )
        );
        return true;
    }

    private void handleFailure(StreamerPremiumFeature premium, Long reportId, Exception e) {
        premium.incrementEmailFailure();
        premium.setLastEmailError(e.getMessage() != null && e.getMessage().length() > 500
                ? e.getMessage().substring(0, 500) : e.getMessage());
        if (premium.getEmailFailureCount() != null && premium.getEmailFailureCount() >= 3) {
            premium.setEmailEnabled(false);
            log.warn("3회 연속 실패 — 이메일 자동 비활성화 streamerNo={}", premium.getStreamerNo());
        }
        premiumRepo.save(premium);
        logRepo.save(EmailReportLog.failure(premium.getStreamerNo(), reportId, e.getMessage()));
    }

    private String resolveEmail(StreamerPremiumFeature premium, User streamer) {
        if (premium.getReportEmailEnc() != null && tokenCryptor.isConfigured()) {
            try { return tokenCryptor.decrypt(premium.getReportEmailEnc()); }
            catch (Exception e) { log.warn("이메일 복호화 실패 streamerNo={}", premium.getStreamerNo(), e); }
        }
        return streamer.getEmail();
    }

    private String generateIntro(User streamer, StreamAnalysisReport report) {
        try {
            if (!gemini.isConfigured()) throw new IllegalStateException("gemini 미설정");
            String prompt = String.format("""
                    스트리머: %s
                    방송 길이: %d분
                    최고 동시 시청자: %s
                    전반적 분위기: %s
                    """,
                    streamer.getNickname(),
                    nz(report.getDurationMinutes()),
                    report.getPeakViewerCount() != null ? report.getPeakViewerCount().toString() : "데이터 없음",
                    safe(report.getAiMood(), "평온"));
            return gemini.generateText(
                    gemini.getLiteModel(),
                    """
                    방송 분석 리포트 이메일의 개인화된 인트로 문단을 작성합니다.
                    - 스트리머를 존중하는 존댓말
                    - 2~3 문장, 총 80자 내외
                    - 숫자 하나 정도만 자연스럽게 언급
                    - 과장 없이 친근하게
                    - 광고성 문구 금지
                    - 마크다운·코드블록 없이 순수 텍스트만 반환
                    """,
                    prompt, 200, false, "EMAIL_INTRO", "streamer:" + streamer.getId());
        } catch (Exception e) {
            log.warn("이메일 인트로 생성 실패 — fallback", e);
            return String.format("%s님, 오늘도 방송하시느라 수고 많으셨어요. 아래 리포트를 확인해보세요.",
                    streamer.getNickname());
        }
    }

    private String createUnsubscribeToken(Long streamerNo) {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokenRepo.save(EmailUnsubscribeToken.builder()
                .token(token)
                .streamerNo(streamerNo)
                .createdAt(LocalDateTime.now(SEOUL))
                .build());
        return token;
    }

    private int nz(Integer v) { return v == null ? 0 : v; }

    private String safe(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }

    private String resolveStreamTitle(StreamAnalysisReport report) {
        try {
            return historyRepo.findByStreamId(report.getStreamId())
                    .map(h -> h.getTitle() != null ? h.getTitle() : "")
                    .orElse("");
        } catch (Exception e) {
            return "";
        }
    }

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd (E)", Locale.KOREAN);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private String formatPeriod(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return "";
        return start.format(DATE_FMT) + " " + start.format(TIME_FMT) + " ~ " + end.format(TIME_FMT);
    }

    private String formatDuration(Integer minutes) {
        int m = minutes == null ? 0 : minutes;
        if (m < 60) return m + "분";
        int h = m / 60;
        int rm = m % 60;
        return rm == 0 ? (h + "시간") : (h + "시간 " + rm + "분");
    }

    private List<String> parseKeywords(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        // 쉼표 구분 + JSON 배열 둘 다 허용
        String trimmed = raw.trim();
        if (trimmed.startsWith("[")) {
            try {
                JsonNode node = om.readTree(trimmed);
                if (node.isArray()) {
                    for (JsonNode n : node) {
                        String s = n.asText();
                        if (s != null && !s.isBlank()) out.add(s.trim());
                    }
                    return out;
                }
            } catch (Exception ignored) {}
        }
        for (String p : trimmed.split(",")) {
            String s = p.trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    public record Moment(String time, String title, String detail) {}

    private List<Moment> parseMoments(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        try {
            JsonNode node = om.readTree(raw);
            if (!node.isArray()) return Collections.emptyList();
            List<Moment> list = new ArrayList<>();
            for (JsonNode n : node) {
                String time = n.path("time").asText("");
                String title = n.path("title").asText("");
                String detail = n.path("detail").asText("");
                if (time.isBlank() && title.isBlank() && detail.isBlank()) continue;
                list.add(new Moment(time, title, detail));
            }
            return list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<String> parseTips(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        try {
            JsonNode node = om.readTree(raw);
            if (node.isArray()) {
                List<String> list = new ArrayList<>();
                for (JsonNode n : node) {
                    String s = n.asText();
                    if (s != null && !s.isBlank()) list.add(s.trim());
                }
                return list;
            }
        } catch (Exception ignored) {}
        List<String> list = new ArrayList<>();
        for (String line : raw.split("\n")) {
            String s = line.trim();
            if (s.startsWith("-")) s = s.substring(1).trim();
            if (!s.isEmpty()) list.add(s);
        }
        return list;
    }
}
