package com.najacks.backend.domain.report.weekly.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.najacks.backend.domain.report.weekly.dto.WeeklyInsight;
import com.najacks.backend.domain.report.weekly.dto.WeeklyStats;
import com.najacks.backend.notion.NotionClient;
import com.najacks.backend.notion.NotionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeeklyReportNotionSync {

    private final NotionClient notion;

    @Value("${app.notion.database.weekly-reports:}")
    private String dbId;

    public boolean isConfigured() {
        return notion.isConfigured() && dbId != null && !dbId.isBlank();
    }

    public String createReport(WeeklyStats stats, WeeklyInsight insight) {
        if (!isConfigured()) {
            log.info("Notion 주간 DB 미설정 — 페이지 생성 스킵 week={}", stats.getWeekLabel());
            return null;
        }
        DateTimeFormatter df = DateTimeFormatter.ofPattern("M/d");
        String title = String.format("%s 주간 리포트 (%s ~ %s)",
                stats.getWeekLabel(),
                stats.getWeekStart().format(df),
                stats.getWeekEnd().format(df));

        // DAU 평균 계산
        int dauAvg = 0;
        if (stats.getDailyActive() != null && !stats.getDailyActive().isEmpty()) {
            int sum = 0, cnt = 0;
            for (Map<String, Object> m : stats.getDailyActive()) {
                sum += toInt(m.get("dau"));
                cnt++;
            }
            dauAvg = cnt > 0 ? sum / cnt : 0;
        }

        // 인사이트 본문 문자열 (highlights + concerns + recommendation 합본)
        StringBuilder insightText = new StringBuilder();
        if (insight.getHighlights() != null) {
            for (String h : insight.getHighlights()) insightText.append("• ").append(h).append("\n");
        }
        if (insight.getConcerns() != null && !insight.getConcerns().isEmpty()) {
            insightText.append("\n⚠️ ");
            for (String c : insight.getConcerns()) insightText.append(c).append(" / ");
        }
        if (insight.getRecommendation() != null) {
            insightText.append("\n🎯 ").append(insight.getRecommendation());
        }

        Map<String, ObjectNode> props = new LinkedHashMap<>();
        // 실제 Notion DB 속성에 맞춘 매핑
        props.put("제목", NotionProperties.title(title));
        props.put("주차", NotionProperties.richText(stats.getWeekLabel()));
        props.put("기간 시작", NotionProperties.date(stats.getWeekStart()));
        props.put("기간 끝", NotionProperties.date(stats.getWeekEnd()));
        props.put("WAU", NotionProperties.number(stats.getWau()));
        props.put("DAU 평균", NotionProperties.number(dauAvg));
        props.put("신규 가입", NotionProperties.number(stats.getNewUsers()));
        props.put("신규 스트리머", NotionProperties.number(stats.getNewStreamers()));
        props.put("신고 건수", NotionProperties.number(toInt(stats.getReportStats().get("total"))));
        props.put("AI 요약", NotionProperties.richText(insight.getHeadline()));
        props.put("인사이트", NotionProperties.richText(insightText.toString()));
        props.put("작성일", NotionProperties.date(java.time.LocalDate.now()));

        List<ObjectNode> body = new ArrayList<>();
        body.add(NotionProperties.callout("💡", Objects.requireNonNullElse(insight.getHeadline(), "")));

        body.add(NotionProperties.heading2("📈 이번 주 하이라이트"));
        if (insight.getHighlights() != null) {
            for (String h : insight.getHighlights()) body.add(NotionProperties.bulletedListItem(h));
        }

        if (insight.getConcerns() != null && !insight.getConcerns().isEmpty()) {
            body.add(NotionProperties.heading2("⚠️ 주목할 지표"));
            for (String c : insight.getConcerns()) body.add(NotionProperties.bulletedListItem(c));
        }

        body.add(NotionProperties.heading2("📊 주요 지표"));
        body.add(NotionProperties.paragraph(String.format(
                "신규 가입: %d명 (전주 대비 %+.1f%%) / 신규 스트리머: %d명 / 누적 회원: %d명 / WAU: %d명 (전주 대비 %+.1f%%)",
                stats.getNewUsers(), stats.getNewUsersChangePct(),
                stats.getNewStreamers(), stats.getTotalUsers(),
                stats.getWau(), stats.getWauChangePct())));

        if (stats.getDailyActive() != null && !stats.getDailyActive().isEmpty()) {
            StringBuilder sb = new StringBuilder("요일별 DAU: ");
            for (Map<String, Object> m : stats.getDailyActive()) {
                sb.append(m.get("visit_date")).append("(").append(m.get("dau")).append(") ");
            }
            body.add(NotionProperties.paragraph(sb.toString().trim()));
        }

        body.add(NotionProperties.heading2("🎯 다음 주 제안"));
        body.add(NotionProperties.paragraph(
                Objects.requireNonNullElse(insight.getRecommendation(), "(제안 없음)")));

        // DB 스키마에 없는 속성 자동 필터
        Map<String, String> schema = notion.getDatabaseSchema(dbId);
        Map<String, ObjectNode> filtered = filterBySchema(props, schema);

        try {
            String pageId = notion.createPage(dbId, filtered, body);
            log.info("주간 리포트 Notion 페이지 생성 완료 week={} pageId={}",
                    stats.getWeekLabel(), pageId);
            return pageId;
        } catch (Exception e) {
            log.warn("주간 리포트 Notion 페이지 생성 실패 week={}", stats.getWeekLabel(), e);
            throw e;
        }
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

    private int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return 0; }
    }
}
