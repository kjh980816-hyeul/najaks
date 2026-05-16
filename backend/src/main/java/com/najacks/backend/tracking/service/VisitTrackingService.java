package com.najacks.backend.tracking.service;

import com.najacks.backend.tracking.repository.UserDailyVisitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class VisitTrackingService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final UserDailyVisitRepository repo;

    @Async("visitExecutor")
    @Transactional
    public void recordVisit(Long userNo) {
        try {
            repo.upsertVisit(userNo, LocalDate.now(SEOUL));
        } catch (Exception e) {
            log.warn("방문 기록 실패 userNo={}", userNo, e);
        }
    }
}
