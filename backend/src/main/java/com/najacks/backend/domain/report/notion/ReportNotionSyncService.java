package com.najacks.backend.domain.report.notion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.najacks.backend.domain.report.entity.Report;
import com.najacks.backend.notion.NotionClient;
import com.najacks.backend.notion.NotionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportNotionSyncService {

    private final NotionClient notion;

    @Value("${app.notion.database.reports:}")
    private String dbId;

    @Value("${app.site.base-url:https://najaks.co.kr}")
    private String siteBaseUrl;

    public boolean isConfigured() {
        return notion.isConfigured() && dbId != null && !dbId.isBlank();
    }

    public String createReportPage(Report r) {
        if (!isConfigured()) {
            log.info("Notion 미설정 — 리포트 페이지 생성 스킵 reportId={}", r.getId());
            return null;
        }
        String categoryName = r.getAiCategory() != null ? r.getAiCategory().name() : "ETC";
        int severity = r.getAiSeverity() != null ? r.getAiSeverity() : 0;
        String title = String.format("#%d [%s] Lv%d - %s",
                r.getId(), categoryName, severity,
                truncate(r.getAiSummary() != null ? r.getAiSummary() : r.getReason(), 80));

        Map<String, ObjectNode> props = new LinkedHashMap<>();
        // 실제 Notion DB 속성명에 맞춘 매핑
        props.put("제목", NotionProperties.title(title));
        props.put("신고 ID", NotionProperties.number(r.getId()));
        props.put("카테고리", NotionProperties.select(categoryName));
        props.put("위험도", NotionProperties.select("Lv" + severity));
        props.put("상태", NotionProperties.status(mapStatus(r)));
        props.put("신고자", NotionProperties.richText(String.valueOf(r.getReporter().getId())));
        props.put("신고 대상", NotionProperties.richText(r.getTargetType().name() + ":" + r.getTargetId()));
        props.put("신고 유형", NotionProperties.select(r.getTargetType().name()));
        props.put("AI 판단 요약", NotionProperties.richText(r.getAiSummary()));
        props.put("신고 사유(원문)", NotionProperties.richText(truncate(r.getReason(), 2000)));
        props.put("신고 일시", NotionProperties.date(
                r.getCreatedAt() != null ? r.getCreatedAt().toLocalDate() : LocalDate.now()));
        if (r.getAdminNote() != null) {
            props.put("메모", NotionProperties.richText(r.getAdminNote()));
        }

        // DB에 없는 속성(원본 링크, 키워드 등)은 스키마 필터링으로 자동 스킵
        Map<String, String> schema = notion.getDatabaseSchema(dbId);
        Map<String, ObjectNode> filtered = filterBySchema(props, schema);

        List<ObjectNode> children = List.of(
                NotionProperties.heading2("신고 원문"),
                NotionProperties.paragraph(r.getReason())
        );

        try {
            String pageId = notion.createPage(dbId, filtered, children);
            log.info("Notion 리포트 페이지 생성 완료 reportId={} pageId={}", r.getId(), pageId);
            return pageId;
        } catch (Exception e) {
            log.warn("Notion 리포트 페이지 생성 실패 reportId={}", r.getId(), e);
            return null;
        }
    }

    public List<JsonNode> fetchRecentlyEditedPages() {
        if (!isConfigured()) return List.of();
        try {
            return notion.queryDatabase(dbId, null, null);
        } catch (Exception e) {
            log.warn("Notion 리포트 DB 쿼리 실패", e);
            return List.of();
        }
    }

    private String mapStatus(Report r) {
        if (r.getStatus() == null) return "접수";
        return switch (r.getStatus()) {
            case PENDING -> "접수";
            case DISMISSED -> "반려";
            case DELETED, SUSPENDED -> "처리완료";
        };
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

    private String truncate(String s, int limit) {
        if (s == null) return "";
        return s.length() <= limit ? s : s.substring(0, limit);
    }
}
