package com.najacks.backend.domain.stream.service;

import com.najacks.backend.domain.notification.entity.NotificationType;
import com.najacks.backend.domain.notification.service.NotificationService;
import com.najacks.backend.domain.stream.entity.StreamerLiveHistory;
import com.najacks.backend.domain.stream.entity.StreamerLiveState;
import com.najacks.backend.domain.stream.event.StreamEndedEvent;
import com.najacks.backend.domain.stream.event.StreamStartedEvent;
import com.najacks.backend.domain.stream.repository.StreamerLiveHistoryRepository;
import com.najacks.backend.domain.stream.repository.StreamerLiveStateRepository;
import com.najacks.backend.domain.user.entity.StreamerProfile;
import com.najacks.backend.domain.user.entity.User;
import com.najacks.backend.domain.user.repository.StreamerProfileRepository;
import com.najacks.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class StreamEventListeners {

    private final StreamerLiveStateRepository stateRepo;
    private final StreamerLiveHistoryRepository historyRepo;
    private final StreamerProfileRepository profileRepo;
    private final UserRepository userRepo;
    private final NotificationService notificationService;
    private final StreamerNotionSyncService notionSync;

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    // ================= 방송 시작 =================

    @Async("notionSyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStreamStartedHistory(StreamStartedEvent ev) {
        try {
            String streamId = ev.detail() != null ? ev.detail().getLiveId() : null;
            // 동일 streamId 가 이미 저장돼 있으면 중복 생성 방지 (재시작·이벤트 재발행 대비)
            if (streamId != null && historyRepo.findByStreamId(streamId).isPresent()) {
                return;
            }
            LocalDateTime started = parseChzzkOpenDate(ev.detail() != null ? ev.detail().getOpenDate() : null);
            if (started == null) started = LocalDateTime.now(SEOUL);
            StreamerLiveHistory h = StreamerLiveHistory.builder()
                    .streamerNo(ev.streamerNo())
                    .platform("CHZZK")
                    .streamId(streamId)
                    .title(ev.detail() != null ? ev.detail().getLiveTitle() : null)
                    .category(ev.detail() != null ? ev.detail().getLiveCategoryValue() : null)
                    .startedAt(started)
                    .peakViewerCount(ev.detail() != null ? ev.detail().getConcurrentUserCount() : null)
                    .build();
            historyRepo.save(h);
        } catch (Exception e) {
            log.warn("방송 이력 저장 실패 streamerNo={}", ev.streamerNo(), e);
        }
    }

    /** 치지직이 주는 openDate는 "yyyy-MM-dd HH:mm:ss" (KST) — naive LocalDateTime 로 파싱. */
    private static final DateTimeFormatter CHZZK_OPEN_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private LocalDateTime parseChzzkOpenDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDateTime.parse(s.trim(), CHZZK_OPEN_DATE_FMT); }
        catch (Exception e) { return null; }
    }

    @Async("aiTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStreamStartedNotify(StreamStartedEvent ev) {
        try {
            User streamer = userRepo.findById(ev.streamerNo()).orElse(null);
            if (streamer == null) return;
            String title = ev.detail() != null && ev.detail().getLiveTitle() != null
                    ? ev.detail().getLiveTitle() : "방송 시작";
            // MVP: 본인에게 노티 표시 (팔로워 시스템이 없으므로 스트리머 자신에게 알림)
            notificationService.createNotification(
                    streamer.getId(),
                    NotificationType.STREAM_STARTED,
                    streamer.getNickname() + "님의 방송이 시작되었습니다: " + title,
                    ev.streamerNo());
        } catch (Exception e) {
            log.warn("방송 시작 알림 실패 streamerNo={}", ev.streamerNo(), e);
        }
    }

    @Async("notionSyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStreamStartedNotion(StreamStartedEvent ev) {
        try {
            StreamerLiveState state = stateRepo.findById(ev.streamerNo()).orElse(null);
            if (state == null) return;
            if (state.getNotionPageId() == null) {
                User u = userRepo.findById(ev.streamerNo()).orElse(null);
                StreamerProfile p = profileRepo.findByUserId(ev.streamerNo()).orElse(null);
                if (u != null && p != null) {
                    String pageId = notionSync.ensureStreamerPage(state, u, p);
                    if (pageId != null) {
                        state.setNotionPageId(pageId);
                        stateRepo.save(state);
                    }
                }
            }
            notionSync.updateLiveStatus(state, true, ev.detail());
        } catch (Exception e) {
            log.warn("방송 시작 Notion 싱크 실패 streamerNo={}", ev.streamerNo(), e);
        }
    }

    // ================= 방송 종료 =================

    @Async("notionSyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStreamEndedHistory(StreamEndedEvent ev) {
        try {
            StreamerLiveHistory h;
            if (ev.streamId() != null) {
                h = historyRepo.findByStreamId(ev.streamId()).orElse(null);
            } else {
                h = historyRepo.findFirstByStreamerNoAndEndedAtIsNullOrderByStartedAtDesc(ev.streamerNo()).orElse(null);
            }
            if (h == null) return;
            LocalDateTime endedAt = LocalDateTime.now(SEOUL);
            h.setEndedAt(endedAt);
            h.setDurationMinutes((int) ChronoUnit.MINUTES.between(h.getStartedAt(), endedAt));
            historyRepo.save(h);
        } catch (Exception e) {
            log.warn("방송 종료 이력 업데이트 실패 streamerNo={}", ev.streamerNo(), e);
        }
    }

    @Async("notionSyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStreamEndedNotion(StreamEndedEvent ev) {
        try {
            StreamerLiveState state = stateRepo.findById(ev.streamerNo()).orElse(null);
            if (state == null) return;
            notionSync.updateLiveStatus(state, false, null);
        } catch (Exception e) {
            log.warn("방송 종료 Notion 싱크 실패 streamerNo={}", ev.streamerNo(), e);
        }
    }
}
