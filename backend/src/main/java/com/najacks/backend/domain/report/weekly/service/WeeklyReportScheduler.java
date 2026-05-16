package com.najacks.backend.domain.report.weekly.service;

import com.najacks.backend.discord.DiscordNotifier;
import com.najacks.backend.domain.report.weekly.dto.WeeklyInsight;
import com.najacks.backend.domain.report.weekly.dto.WeeklyStats;
import com.najacks.backend.domain.report.weekly.entity.WeeklyReportRun;
import com.najacks.backend.domain.report.weekly.repository.WeeklyReportRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeeklyReportScheduler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final WeeklyStatsService statsService;
    private final WeeklyInsightGenerator insightGenerator;
    private final WeeklyReportNotionSync notionSync;
    private final DiscordNotifier discord;
    private final WeeklyReportRunRepository runRepo;

    /** 매주 월요일 09:00 KST */
    @Scheduled(cron = "0 0 9 ? * MON", zone = "Asia/Seoul")
    public void weeklyRun() {
        LocalDate today = LocalDate.now(SEOUL);
        LocalDate[] range = WeeklyStatsService.lastWeekRange(today);
        runForWeek(range[0], range[1], false);
    }

    /** 관리자 수동 트리거: 특정 주차 재생성 (force=true 면 이미 SUCCESS여도 다시 생성) */
    public WeeklyReportRun runForWeek(LocalDate start, LocalDate end, boolean force) {
        String weekLabel = WeeklyStatsService.weekLabelOf(start);

        if (!force && runRepo.existsByWeekLabelAndStatus(weekLabel, "SUCCESS")) {
            log.info("이미 성공한 주차 스킵: {}", weekLabel);
            return runRepo.findByWeekLabel(weekLabel).orElse(null);
        }

        WeeklyReportRun run = runRepo.findByWeekLabel(weekLabel)
                .orElseGet(() -> WeeklyReportRun.start(weekLabel, start, end));
        run.setStatus("RUNNING");
        run.setErrorMessage(null);
        run.setNotionPageId(null);
        run = runRepo.save(run);

        try {
            WeeklyStats stats = statsService.collect(start, end);
            WeeklyInsight insight = insightGenerator.generate(stats);
            String pageId = notionSync.createReport(stats, insight);

            if (pageId != null) {
                String url = "https://www.notion.so/" + pageId.replace("-", "");
                discord.sendNoticeWithUrl(
                        "📊 " + weekLabel + " 주간 리포트",
                        insight.getHeadline() != null ? insight.getHeadline() : "자동 생성됨",
                        url,
                        0x5B8DEF);
                run.markSuccess(pageId);
            } else {
                // Notion 미설정 시에도 성공으로 처리 (데이터 수집만 완료)
                run.setStatus("SUCCESS_NO_NOTION");
                run.setFinishedAt(java.time.LocalDateTime.now());
            }
        } catch (Exception e) {
            log.error("주간 리포트 실패 week={}", weekLabel, e);
            run.markFailed(e.getMessage());
            discord.sendAdmin("⚠️ 주간 리포트 실패 " + weekLabel,
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(),
                    0xDD2222);
        } finally {
            runRepo.save(run);
        }
        return run;
    }
}
