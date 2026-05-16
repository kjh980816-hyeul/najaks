package com.najacks.backend.domain.report.weekly.controller;

import com.najacks.backend.domain.report.weekly.entity.WeeklyReportRun;
import com.najacks.backend.domain.report.weekly.service.WeeklyReportScheduler;
import com.najacks.backend.domain.report.weekly.service.WeeklyStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

@RestController
@RequestMapping("/api/admin/weekly-reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class WeeklyReportController {

    private final WeeklyReportScheduler scheduler;

    /** 지난 주를 강제 재생성 */
    @PostMapping("/run-last-week")
    public ResponseEntity<WeeklyReportRun> runLastWeek(
            @RequestParam(defaultValue = "false") boolean force) {
        LocalDate today = LocalDate.now();
        LocalDate[] range = WeeklyStatsService.lastWeekRange(today);
        WeeklyReportRun run = scheduler.runForWeek(range[0], range[1], force);
        return ResponseEntity.ok(run);
    }

    /** 임의 날짜를 포함한 주차를 생성 (월~일) */
    @PostMapping("/run")
    public ResponseEntity<WeeklyReportRun> runByDate(
            @RequestParam("anyDateInWeek") String anyDateInWeek,
            @RequestParam(defaultValue = "true") boolean force) {
        LocalDate date = LocalDate.parse(anyDateInWeek);
        LocalDate start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(6);
        WeeklyReportRun run = scheduler.runForWeek(start, end, force);
        return ResponseEntity.ok(run);
    }
}
