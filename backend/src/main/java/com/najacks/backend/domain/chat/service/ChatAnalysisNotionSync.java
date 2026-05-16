package com.najacks.backend.domain.chat.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.najacks.backend.domain.chat.entity.ChatHighlight;
import com.najacks.backend.domain.chat.entity.StreamAnalysisReport;
import com.najacks.backend.domain.chat.entity.StreamerPremiumFeature;
import com.najacks.backend.domain.chat.crypto.TokenCryptor;
import com.najacks.backend.domain.chat.repository.ChatHighlightRepository;
import com.najacks.backend.domain.chat.repository.StreamerPremiumFeatureRepository;
import com.najacks.backend.domain.user.entity.User;
import com.najacks.backend.domain.user.repository.UserRepository;
import com.najacks.backend.notion.NotionClient;
import com.najacks.backend.notion.NotionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatAnalysisNotionSync {

    private final NotionClient notion;
    private final UserRepository userRepo;
    private final ChatHighlightRepository highlightRepo;
    private final StreamerPremiumFeatureRepository premiumRepo;
    private final TokenCryptor cryptor;

    @Value("${app.notion.database.stream-analysis:}")
    private String dbId;

    public boolean isConfigured() {
        return notion.isConfigured() && dbId != null && !dbId.isBlank();
    }

    public String createAnalysisPage(StreamAnalysisReport report) {
        if (!isConfigured()) {
            log.info("채팅분석 Notion DB 미설정 — 페이지 생성 스킵 reportId={}", report.getId());
            return null;
        }

        User user = userRepo.findById(report.getStreamerNo()).orElse(null);
        String nickname = user != null ? user.getNickname() : "(알 수 없음)";

        String title = String.format("%s - %s 방송 분석",
                nickname,
                report.getStartedAt() != null
                        ? report.getStartedAt().format(DateTimeFormatter.ofPattern("M/d HH:mm"))
                        : "");

        List<ChatHighlight> highlights = highlightRepo.findByStreamIdOrderByOccurredAtAsc(report.getStreamId());
        String highlightSummary = highlights.isEmpty() ? "(하이라이트 없음)" :
                highlights.stream()
                        .limit(10)
                        .map(h -> "• " + h.getOccurredAt().format(DateTimeFormatter.ofPattern("HH:mm")) + " " +
                                (h.getAiSummary() != null ? h.getAiSummary() : "(요약 없음)"))
                        .collect(Collectors.joining("\n"));

        List<String> keywords = report.getAiTopKeywords() != null && !report.getAiTopKeywords().isBlank()
                ? Arrays.stream(report.getAiTopKeywords().split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).limit(10).toList()
                : List.of();

        String recipientEmail = resolveRecipientEmail(report.getStreamerNo(), user);

        Map<String, ObjectNode> props = new LinkedHashMap<>();
        props.put("제목", NotionProperties.title(title));
        props.put("스트리머", NotionProperties.richText(nickname));
        props.put("방송 일시", NotionProperties.date(
                report.getStartedAt() != null ? report.getStartedAt().toLocalDate() : null));
        props.put("참여 시청자", NotionProperties.number(report.getPeakViewerCount()));
        props.put("총 채팅 수", NotionProperties.number(report.getTotalChatCount()));
        props.put("AI 총평", NotionProperties.richText(report.getAiSummary()));
        props.put("주요 하이라이트", NotionProperties.richText(highlightSummary));
        props.put("주요 감성", NotionProperties.select(report.getAiMood()));
        props.put("핫 키워드", NotionProperties.multiSelect(keywords));
        if (recipientEmail != null) {
            ObjectNode emailProp = JsonNodeFactory.instance.objectNode();
            emailProp.put("email", recipientEmail);
            props.put("수신자 이메일", emailProp);
        }
        props.put("이메일 발송됨", NotionProperties.checkbox(false));

        Map<String, String> schema = notion.getDatabaseSchema(dbId);
        Map<String, ObjectNode> filtered = filterBySchema(props, schema);

        try {
            String pageId = notion.createPage(dbId, filtered, null);
            log.info("채팅분석 Notion 페이지 생성 완료 reportId={} pageId={}", report.getId(), pageId);
            return pageId;
        } catch (Exception e) {
            log.warn("채팅분석 Notion 페이지 생성 실패 reportId={}", report.getId(), e);
            return null;
        }
    }

    /** 이메일 리포트와 동일한 섹션을 Notion 페이지 본문에 append. */
    public void populatePageBody(String notionPageId, PageBodyParams p) {
        if (!isConfigured() || notionPageId == null) return;
        List<ObjectNode> blocks = new ArrayList<>();

        // 방송 일시
        StringBuilder periodLine = new StringBuilder();
        if (p.streamPeriod() != null && !p.streamPeriod().isBlank()) {
            periodLine.append("📅 ").append(p.streamPeriod());
        }
        if (p.durationFormatted() != null && !p.durationFormatted().isBlank()) {
            if (periodLine.length() > 0) periodLine.append("  ·  ");
            periodLine.append(p.durationFormatted());
        }
        if (periodLine.length() > 0) blocks.add(NotionProperties.callout("📅", periodLine.toString()));

        // 개인화 인트로
        if (p.aiIntro() != null && !p.aiIntro().isBlank()) {
            blocks.add(NotionProperties.callout("✨", p.aiIntro()));
        }

        // 방송 지표
        blocks.add(NotionProperties.heading2("📊 방송 지표"));
        blocks.add(NotionProperties.paragraph(String.format(
                "최고 동시 시청자: %s  ·  하이라이트: %d개  ·  총 채팅: %d  ·  순 참여자: %d",
                p.peakViewers() == null ? "-" : p.peakViewers(),
                p.highlightCount() == null ? 0 : p.highlightCount(),
                p.totalChats() == null ? 0 : p.totalChats(),
                p.uniqueChatters() == null ? 0 : p.uniqueChatters())));
        if (p.analysisMood() != null && !p.analysisMood().isBlank()) {
            blocks.add(NotionProperties.paragraph("전반적 분위기: " + p.analysisMood()));
        }

        // 주요 키워드
        if (p.keywordsList() != null && !p.keywordsList().isEmpty()) {
            blocks.add(NotionProperties.heading2("🏷️ 주요 키워드"));
            String kw = p.keywordsList().stream().map(s -> "#" + s).collect(Collectors.joining("  "));
            blocks.add(NotionProperties.paragraph(kw));
        }

        // AI 요약
        if (p.aiSummary() != null && !p.aiSummary().isBlank()) {
            blocks.add(NotionProperties.heading2("💡 AI 요약"));
            for (String para : splitByLimit(p.aiSummary(), 1900)) {
                blocks.add(NotionProperties.paragraph(para));
            }
        }

        // 시간대별 주요 순간
        if (p.momentsList() != null && !p.momentsList().isEmpty()) {
            blocks.add(NotionProperties.heading2("🎯 시간대별 주요 순간"));
            for (MomentItem m : p.momentsList()) {
                StringBuilder line = new StringBuilder();
                if (m.time() != null && !m.time().isBlank()) line.append("[").append(m.time()).append("] ");
                if (m.title() != null && !m.title().isBlank()) line.append(m.title());
                if (m.detail() != null && !m.detail().isBlank()) line.append(" — ").append(m.detail());
                if (line.length() > 0) blocks.add(NotionProperties.bulletedListItem(line.toString()));
            }
        }

        // 시청자 참여 분석
        if (p.audienceInsight() != null && !p.audienceInsight().isBlank()) {
            blocks.add(NotionProperties.heading2("👥 시청자 참여 분석"));
            for (String para : splitByLimit(p.audienceInsight(), 1900)) {
                blocks.add(NotionProperties.paragraph(para));
            }
        }

        // 다음 방송 팁
        if (p.tipsList() != null && !p.tipsList().isEmpty()) {
            blocks.add(NotionProperties.heading2("💬 다음 방송 팁"));
            for (String t : p.tipsList()) {
                if (t != null && !t.isBlank()) blocks.add(NotionProperties.bulletedListItem(t));
            }
        }

        try {
            // Notion 은 한 번에 최대 100 블록. 안전하게 배치 분할
            int batchSize = 90;
            for (int i = 0; i < blocks.size(); i += batchSize) {
                List<ObjectNode> chunk = blocks.subList(i, Math.min(i + batchSize, blocks.size()));
                notion.appendBlockChildren(notionPageId, chunk);
            }
            log.info("채팅분석 Notion 본문 블록 추가 완료 pageId={} blocks={}", notionPageId, blocks.size());
        } catch (Exception e) {
            log.warn("채팅분석 Notion 본문 블록 추가 실패 pageId={}", notionPageId, e);
        }
    }

    private List<String> splitByLimit(String text, int limit) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (int i = 0; i < text.length(); i += limit) {
            out.add(text.substring(i, Math.min(i + limit, text.length())));
        }
        return out;
    }

    public record MomentItem(String time, String title, String detail) {}

    public record PageBodyParams(
            String aiIntro,
            String streamPeriod,
            String durationFormatted,
            String peakViewers,
            Integer totalChats,
            Integer uniqueChatters,
            Integer highlightCount,
            String analysisMood,
            String aiSummary,
            List<String> keywordsList,
            String audienceInsight,
            List<MomentItem> momentsList,
            List<String> tipsList
    ) {}

    public void markEmailSent(String notionPageId) {
        if (!isConfigured() || notionPageId == null) return;
        Map<String, ObjectNode> props = new LinkedHashMap<>();
        props.put("이메일 발송됨", NotionProperties.checkbox(true));
        Map<String, String> schema = notion.getDatabaseSchema(dbId);
        Map<String, ObjectNode> filtered = filterBySchema(props, schema);
        try {
            notion.updatePageProperties(notionPageId, filtered);
        } catch (Exception e) {
            log.warn("이메일 발송됨 체크박스 업데이트 실패 pageId={}", notionPageId, e);
        }
    }

    private String resolveRecipientEmail(Long streamerNo, User user) {
        StreamerPremiumFeature f = premiumRepo.findById(streamerNo).orElse(null);
        if (f != null && f.getReportEmailEnc() != null && cryptor.isConfigured()) {
            try { return cryptor.decrypt(f.getReportEmailEnc()); } catch (Exception ignored) {}
        }
        return user != null ? user.getEmail() : null;
    }

    private Map<String, ObjectNode> filterBySchema(
            Map<String, ObjectNode> props, Map<String, String> schema) {
        if (schema.isEmpty()) return props;
        Map<String, ObjectNode> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, ObjectNode> e : props.entrySet()) {
            if (schema.containsKey(e.getKey())) filtered.put(e.getKey(), e.getValue());
        }
        return filtered;
    }
}
