package com.najacks.backend.domain.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.najacks.backend.ai.GeminiClient;
import com.najacks.backend.domain.chat.entity.ChatHighlight;
import com.najacks.backend.domain.chat.entity.ChatMinuteStats;
import com.najacks.backend.domain.chat.entity.StreamAnalysisReport;
import com.najacks.backend.domain.chat.event.AnalysisReportReadyEvent;
import com.najacks.backend.domain.chat.repository.ChatHighlightRepository;
import com.najacks.backend.domain.chat.repository.ChatMinuteStatsRepository;
import com.najacks.backend.domain.chat.repository.StreamAnalysisReportRepository;
import com.najacks.backend.domain.stream.entity.StreamerLiveHistory;
import com.najacks.backend.domain.stream.event.StreamEndedEvent;
import com.najacks.backend.domain.stream.repository.StreamerLiveHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 방송 종료 시 채팅 통계 + 하이라이트 + 대표 채팅 원문을 Gemini에 투입해
 * 상세 분석 리포트를 생성한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StreamAnalysisFinalizer {

    private final PremiumGateService gate;
    private final StreamerLiveHistoryRepository historyRepo;
    private final StreamAnalysisReportRepository reportRepo;
    private final ChatMinuteStatsRepository minuteRepo;
    private final ChatHighlightRepository highlightRepo;
    private final GeminiClient gemini;
    private final ApplicationEventPublisher publisher;
    private final ChatAnalysisNotionSync notionSync;
    private final ObjectMapper om = new ObjectMapper();

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Async("aiTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStreamEnded(StreamEndedEvent ev) {
        if (!gate.isEnabled(ev.streamerNo())) return;
        if (ev.streamId() == null) return;
        if (reportRepo.findByStreamId(ev.streamId()).isPresent()) return;

        // 채팅 수집 flush + endedAt 기록 비동기라 약간 대기
        try { Thread.sleep(3000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); return; }

        StreamerLiveHistory history = historyRepo.findByStreamId(ev.streamId())
                .or(() -> historyRepo.findFirstByStreamerNoAndEndedAtIsNullOrderByStartedAtDesc(ev.streamerNo()))
                .orElse(null);

        LocalDateTime started = history != null && history.getStartedAt() != null
                ? history.getStartedAt() : LocalDateTime.now(SEOUL).minusHours(1);
        LocalDateTime ended = history != null && history.getEndedAt() != null
                ? history.getEndedAt() : LocalDateTime.now(SEOUL);
        int duration = Math.max(0, (int) java.time.temporal.ChronoUnit.MINUTES.between(started, ended));

        String title = history != null ? history.getTitle() : null;
        Integer peak = history != null ? history.getPeakViewerCount() : null;
        String category = history != null ? history.getCategory() : null;

        List<ChatMinuteStats> minutes = minuteRepo.findByStreamIdOrderByMinuteBucketAsc(ev.streamId());
        List<ChatHighlight> highlights = highlightRepo.findByStreamIdOrderByOccurredAtAsc(ev.streamId());

        int totalChats = minutes.stream().mapToInt(ChatMinuteStats::getChatCount).sum();
        int uniqueChatters = minutes.stream().mapToInt(ChatMinuteStats::getUniqueChatters).max().orElse(0);

        AiPayload ai = runAi(title, category, duration, peak, totalChats, uniqueChatters,
                minutes, highlights);

        StreamAnalysisReport report = StreamAnalysisReport.builder()
                .streamerNo(ev.streamerNo())
                .streamId(ev.streamId())
                .startedAt(started)
                .endedAt(ended)
                .durationMinutes(duration)
                .totalChatCount(totalChats)
                .uniqueChatterCount(uniqueChatters)
                .peakViewerCount(peak)
                .aiSummary(ai.summary)
                .aiMood(ai.mood)
                .aiTopKeywords(ai.keywords)
                .aiAudienceInsight(ai.audienceInsight)
                .aiHighlightMoments(ai.highlightMoments)
                .aiImprovementTips(ai.improvementTips)
                .highlightCount(highlights.size())
                .createdAt(LocalDateTime.now(SEOUL))
                .build();
        report = reportRepo.save(report);

        try {
            String pageId = notionSync.createAnalysisPage(report);
            if (pageId != null) {
                report.setNotionPageId(pageId);
                reportRepo.save(report);
            }
        } catch (Exception e) {
            log.warn("Notion 채팅분석 아카이브 실패 reportId={}", report.getId(), e);
        }

        publisher.publishEvent(new AnalysisReportReadyEvent(report.getId(), ev.streamerNo()));
        log.info("방송 분석 리포트 생성 streamerNo={} chats={} highlights={}",
                ev.streamerNo(), totalChats, highlights.size());
    }

    private AiPayload runAi(String title, String category, int duration,
                            Integer peak, int totalChats, int uniqueChatters,
                            List<ChatMinuteStats> minutes, List<ChatHighlight> highlights) {
        try {
            String prompt = buildPrompt(title, category, duration, peak,
                    totalChats, uniqueChatters, minutes, highlights);

            @SuppressWarnings("unchecked")
            Map<String, Object> result = gemini.generateJson(
                    gemini.getDefaultModel(),
                    """
                    당신은 한국 스트리밍 방송 채팅·시청 데이터를 분석하는 전문 데이터 분석가입니다.
                    제공된 분당 채팅 타임라인, 대표 채팅 원문, 하이라이트 원문을 근거로
                    구체적이고 사실적인 방송 리뷰를 작성합니다. 과장·광고성 문구 금지.
                    따뜻한 존댓말을 사용하되 구체 수치·구체 내용을 반드시 포함하세요.
                    반드시 JSON만 반환합니다.
                    """,
                    prompt,
                    3500,
                    Map.class,
                    "STREAM_ANALYSIS",
                    "stream:" + (minutes.isEmpty() ? "-" : minutes.get(0).getStreamId()));

            AiPayload p = new AiPayload();
            p.summary = str(result.get("summary"));
            p.mood = str(result.get("mood"));
            p.keywords = joinKeywords(result.get("keywords"));
            p.audienceInsight = str(result.get("audienceInsight"));
            p.highlightMoments = formatMoments(result.get("highlightMoments"));
            p.improvementTips = formatTips(result.get("improvementTips"));
            return p;
        } catch (Exception e) {
            log.warn("방송 분석 AI 실패 — fallback", e);
            AiPayload p = new AiPayload();
            p.summary = String.format("%s 방송이 %d분 동안 진행되었습니다. 총 %d개의 채팅이 오갔습니다. 수고 많으셨습니다.",
                    nullTo(title, "오늘"), duration, totalChats);
            p.mood = totalChats > 500 ? "활기찬 방송" : "차분한 방송";
            p.keywords = "";
            p.audienceInsight = "";
            p.highlightMoments = "";
            p.improvementTips = "";
            return p;
        }
    }

    private String buildPrompt(String title, String category, int duration, Integer peak,
                               int totalChats, int uniqueChatters,
                               List<ChatMinuteStats> minutes, List<ChatHighlight> highlights) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 방송 메타\n");
        sb.append("- 제목: ").append(nullTo(title, "(제목 없음)")).append("\n");
        sb.append("- 카테고리: ").append(nullTo(category, "(미설정)")).append("\n");
        sb.append("- 방송 길이: ").append(duration).append("분\n");
        sb.append("- 최고 동시 시청자: ").append(peak != null ? peak : "알 수 없음").append("명\n");
        sb.append("- 총 채팅 수: ").append(totalChats).append("건\n");
        sb.append("- 순 참여자(분당 최대 기준): ").append(uniqueChatters).append("명\n\n");

        sb.append("## 분당 채팅 타임라인 (시간 | 채팅수 | 순참여자)\n");
        int limit = Math.min(minutes.size(), 120);
        for (int i = 0; i < limit; i++) {
            ChatMinuteStats m = minutes.get(i);
            sb.append("- ").append(m.getMinuteBucket().format(HHMM))
                    .append(" | ").append(m.getChatCount())
                    .append(" | ").append(m.getUniqueChatters())
                    .append("\n");
        }
        if (minutes.size() > limit) sb.append("- ...(이후 ").append(minutes.size() - limit).append("분 생략)\n");
        sb.append("\n");

        sb.append("## 대표 채팅 원문 샘플 (시간대 고르게)\n");
        int samplesAdded = 0;
        int stride = Math.max(1, minutes.size() / 20);
        for (int i = 0; i < minutes.size() && samplesAdded < 60; i += stride) {
            ChatMinuteStats m = minutes.get(i);
            if (m.getSampleMessages() == null) continue;
            sb.append("[").append(m.getMinuteBucket().format(HHMM)).append("] ")
                    .append(m.getSampleMessages()).append("\n");
            samplesAdded++;
        }
        if (samplesAdded == 0) sb.append("(분당 샘플 미수집 — 이번 방송 기준 원문 샘플 없음)\n");
        sb.append("\n");

        sb.append("## 하이라이트 (CPS 급상승 순간)\n");
        if (highlights.isEmpty()) {
            sb.append("(감지된 하이라이트 없음)\n");
        } else {
            int hLimit = Math.min(highlights.size(), 10);
            for (int i = 0; i < hLimit; i++) {
                ChatHighlight h = highlights.get(i);
                sb.append(i + 1).append(". ")
                        .append(h.getOccurredAt().format(HHMM))
                        .append(" — magnitude ").append(String.format("%.1f", h.getMagnitude() == null ? 0f : h.getMagnitude()))
                        .append("배 — ").append(h.getAiSummary() != null ? h.getAiSummary() : "(요약 없음)")
                        .append("\n   원문샘플: ").append(h.getSampleMessages() != null ? h.getSampleMessages() : "(없음)")
                        .append("\n");
            }
        }
        sb.append("\n");

        sb.append("""
                ## 출력 JSON 스키마 (반드시 이 형태 그대로)
                {
                  "summary": "3~4문장, 이 방송 특유의 내용을 구체 수치와 함께 서술. 일반론 금지.",
                  "mood": "방송 전반적 분위기 한 단어 또는 짧은 구 (예: '활기찬 방송', '차분한 건축 방송')",
                  "keywords": ["이 방송 특징을 드러내는 단어 5~8개", "해시태그 없이 순수 단어만"],
                  "audienceInsight": "시청자 참여 패턴을 2~3문장으로. 분당 채팅 타임라인과 순참여자 비율을 근거로.",
                  "highlightMoments": [
                    {"time": "HH:mm", "title": "이벤트 한줄 제목", "detail": "무슨 일이 있었는지 1~2문장"}
                  ],
                  "improvementTips": ["다음 방송에 도움될 구체 팁 2~4개. 일반론 금지, 이 방송 패턴에서 도출된 제안만."]
                }
                highlightMoments는 최소 2개 이상 포함하세요. 하이라이트가 없더라도 분당 타임라인에서 채팅 피크 순간을 찾아 2개 이상 만드세요.
                """);
        return sb.toString();
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    private String joinKeywords(Object v) {
        if (v instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(list.size(), 10); i++) {
                if (i > 0) sb.append(", ");
                sb.append(String.valueOf(list.get(i)));
            }
            return sb.toString();
        }
        return str(v);
    }

    @SuppressWarnings("unchecked")
    private String formatMoments(Object v) {
        if (!(v instanceof List<?> list)) return "";
        try {
            return om.writeValueAsString(list);
        } catch (Exception e) {
            StringBuilder sb = new StringBuilder();
            for (Object o : list) sb.append(o).append("\n");
            return sb.toString();
        }
    }

    @SuppressWarnings("unchecked")
    private String formatTips(Object v) {
        if (!(v instanceof List<?> list)) return "";
        try {
            return om.writeValueAsString(list);
        } catch (Exception e) {
            StringBuilder sb = new StringBuilder();
            for (Object o : list) sb.append("- ").append(o).append("\n");
            return sb.toString();
        }
    }

    private String nullTo(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }

    private static class AiPayload {
        String summary = "";
        String mood = "";
        String keywords = "";
        String audienceInsight = "";
        String highlightMoments = "";
        String improvementTips = "";
    }
}
