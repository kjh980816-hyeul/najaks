package com.najacks.backend.domain.report.weekly.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class WeeklyStats {
    private String weekLabel;
    private LocalDate weekStart;
    private LocalDate weekEnd;

    private int newUsers;
    private double newUsersChangePct;

    private int newStreamers;

    private int totalUsers;

    private int wau;
    private double wauChangePct;

    /** 요일별 DAU: [{visit_date, dau}] */
    private List<Map<String, Object>> dailyActive;

    /** 신고 통계 */
    private Map<String, Object> reportStats;
}
