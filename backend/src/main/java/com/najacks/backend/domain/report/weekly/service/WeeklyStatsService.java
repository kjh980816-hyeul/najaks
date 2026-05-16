package com.najacks.backend.domain.report.weekly.service;

import com.najacks.backend.domain.report.weekly.dto.WeeklyStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeeklyStatsService {

    private final JdbcTemplate jdbc;

    public WeeklyStats collect(LocalDate start, LocalDate end) {
        LocalDate prevStart = start.minusWeeks(1);
        LocalDate prevEnd = end.minusWeeks(1);

        // 신규 가입
        int newUsers = countDateRange("users", "created_at", start, end);
        int prevNewUsers = countDateRange("users", "created_at", prevStart, prevEnd);

        // 신규 스트리머 (StreamerProfile.verified=true AND createdAt in range)
        int newStreamers = safeQueryForInt(
                "SELECT COUNT(*) FROM streamer_profiles WHERE verified = true " +
                        "AND DATE(created_at) BETWEEN ? AND ?",
                start, end);

        // DAU 시리즈
        List<Map<String, Object>> dailyActive = safeQueryForList("""
                SELECT visit_date, COUNT(*) AS dau
                FROM user_daily_visits
                WHERE visit_date BETWEEN ? AND ?
                GROUP BY visit_date ORDER BY visit_date
                """, start, end);

        // WAU
        int wau = safeQueryForInt("""
                SELECT COUNT(DISTINCT user_no) FROM user_daily_visits
                WHERE visit_date BETWEEN ? AND ?
                """, start, end);
        int prevWau = safeQueryForInt("""
                SELECT COUNT(DISTINCT user_no) FROM user_daily_visits
                WHERE visit_date BETWEEN ? AND ?
                """, prevStart, prevEnd);

        // 신고 통계 (processed_at 을 resolved_at 대용으로 사용)
        Map<String, Object> reportStats = safeQueryForMap("""
                SELECT
                    COUNT(*) AS total,
                    SUM(CASE WHEN status IN ('DELETED','SUSPENDED','DISMISSED') THEN 1 ELSE 0 END) AS resolved,
                    SUM(CASE WHEN ai_severity >= 4 THEN 1 ELSE 0 END) AS high_severity,
                    AVG(CASE WHEN processed_at IS NOT NULL
                             THEN TIMESTAMPDIFF(MINUTE, created_at, processed_at) END) AS avg_resolve_min
                FROM reports
                WHERE DATE(created_at) BETWEEN ? AND ?
                """, start, end);

        // 누적 회원
        int totalUsers = safeQueryForInt("SELECT COUNT(*) FROM users");

        return WeeklyStats.builder()
                .weekLabel(weekLabelOf(start))
                .weekStart(start).weekEnd(end)
                .newUsers(newUsers)
                .newUsersChangePct(pct(prevNewUsers, newUsers))
                .newStreamers(newStreamers)
                .wau(wau).wauChangePct(pct(prevWau, wau))
                .dailyActive(dailyActive)
                .totalUsers(totalUsers)
                .reportStats(reportStats == null ? new HashMap<>() : reportStats)
                .build();
    }

    public static String weekLabelOf(LocalDate date) {
        int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int year = date.get(IsoFields.WEEK_BASED_YEAR);
        return String.format("%d-W%02d", year, week);
    }

    public static LocalDate[] lastWeekRange(LocalDate today) {
        LocalDate lastMonday = today.minusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new LocalDate[]{lastMonday, lastMonday.plusDays(6)};
    }

    private int countDateRange(String table, String column, LocalDate start, LocalDate end) {
        return safeQueryForInt(
                "SELECT COUNT(*) FROM " + table + " WHERE DATE(" + column + ") BETWEEN ? AND ?",
                start, end);
    }

    private int safeQueryForInt(String sql, Object... args) {
        try {
            Integer v = jdbc.queryForObject(sql, Integer.class, args);
            return v == null ? 0 : v;
        } catch (Exception e) {
            log.warn("weekly stats query failed: {} — fallback 0", sql.split("\n")[0].trim(), e);
            return 0;
        }
    }

    private List<Map<String, Object>> safeQueryForList(String sql, Object... args) {
        try {
            return jdbc.queryForList(sql, args);
        } catch (Exception e) {
            log.warn("weekly stats list query failed — fallback empty", e);
            return List.of();
        }
    }

    private Map<String, Object> safeQueryForMap(String sql, Object... args) {
        try {
            return jdbc.queryForMap(sql, args);
        } catch (Exception e) {
            log.warn("weekly stats map query failed — fallback empty", e);
            return new HashMap<>();
        }
    }

    private double pct(int prev, int curr) {
        if (prev == 0) return curr == 0 ? 0.0 : 100.0;
        return ((double) (curr - prev) / prev) * 100.0;
    }
}
