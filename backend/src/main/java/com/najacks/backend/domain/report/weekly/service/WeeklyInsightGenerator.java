package com.najacks.backend.domain.report.weekly.service;

import com.najacks.backend.ai.GeminiClient;
import com.najacks.backend.domain.report.weekly.dto.WeeklyInsight;
import com.najacks.backend.domain.report.weekly.dto.WeeklyStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeeklyInsightGenerator {

    private final GeminiClient gemini;

    private static final String SYSTEM = """
            당신은 스트리머 팬 커뮤니티 '나작스'의 데이터 분석가입니다.
            주간 지표를 분석해 경영진용 인사이트를 한국어로 작성합니다.
            반드시 JSON만 반환합니다.

            규칙:
            - 핵심 변화 3가지만 "highlights"에 포함
            - 이상치·우려사항이 있으면 "concerns"에 기재 (없으면 빈 배열)
            - 숫자는 반드시 인용 (과장 금지)
            - 다음 주 제안 1~2문장 "recommendation"

            스키마:
            {
              "headline": "한 문장 헤드라인",
              "highlights": ["포인트1", "포인트2", "포인트3"],
              "concerns": ["우려사항1"],
              "recommendation": "제안 1~2문장"
            }
            """;

    public WeeklyInsight generate(WeeklyStats stats) {
        Map<String, Object> rs = stats.getReportStats();
        Object avgResolve = rs.getOrDefault("avg_resolve_min", 0);
        Object reportTotal = rs.getOrDefault("total", 0);
        Object reportResolved = rs.getOrDefault("resolved", 0);
        Object reportHigh = rs.getOrDefault("high_severity", 0);

        String userMsg = String.format("""
                주간 지표 (%s ~ %s):
                - 신규 가입: %d명 (전주 대비 %+.1f%%)
                - 신규 스트리머 승인: %d명
                - 누적 회원: %d명
                - WAU: %d명 (전주 대비 %+.1f%%)
                - 요일별 DAU: %s
                - 신고 접수: %s건 / 처리: %s건 / 고심각도: %s건
                - 평균 처리 시간: %s분
                """,
                stats.getWeekStart(), stats.getWeekEnd(),
                stats.getNewUsers(), stats.getNewUsersChangePct(),
                stats.getNewStreamers(), stats.getTotalUsers(),
                stats.getWau(), stats.getWauChangePct(),
                formatDaily(stats.getDailyActive()),
                reportTotal, reportResolved, reportHigh,
                avgResolve);

        try {
            return gemini.generateJson(
                    gemini.getDefaultModel(),
                    SYSTEM,
                    userMsg,
                    2500,
                    WeeklyInsight.class,
                    "WEEKLY_INSIGHT",
                    "week:" + stats.getWeekLabel());
        } catch (Exception e) {
            log.warn("WeeklyInsight 생성 실패 — fallback 기본값 사용", e);
            WeeklyInsight fallback = new WeeklyInsight();
            fallback.setHeadline(String.format("%s 주간 지표 (%d명 가입, WAU %d)",
                    stats.getWeekLabel(), stats.getNewUsers(), stats.getWau()));
            fallback.setHighlights(List.of(
                    "신규 가입 " + stats.getNewUsers() + "명",
                    "WAU " + stats.getWau() + "명",
                    "누적 회원 " + stats.getTotalUsers() + "명"
            ));
            fallback.setConcerns(List.of());
            fallback.setRecommendation("AI 분석이 실패해 기본 요약만 표시됩니다.");
            return fallback;
        }
    }

    private String formatDaily(List<Map<String, Object>> dailyActive) {
        if (dailyActive == null || dailyActive.isEmpty()) return "(데이터 없음)";
        return dailyActive.stream()
                .map(m -> m.get("visit_date") + ":" + m.get("dau"))
                .collect(Collectors.joining(", "));
    }
}
