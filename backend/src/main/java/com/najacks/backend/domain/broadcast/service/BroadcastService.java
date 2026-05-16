package com.najacks.backend.domain.broadcast.service;

import com.najacks.backend.domain.broadcast.dto.BroadcastScheduleRequest;
import com.najacks.backend.domain.broadcast.dto.BroadcastScheduleResponse;
import com.najacks.backend.domain.broadcast.entity.BroadcastSchedule;
import com.najacks.backend.domain.broadcast.repository.BroadcastScheduleRepository;
import com.najacks.backend.domain.user.entity.Role;
import com.najacks.backend.domain.user.entity.User;
import com.najacks.backend.domain.user.repository.UserRepository;
import com.najacks.backend.global.exception.CustomException;
import com.najacks.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BroadcastService {

    private final BroadcastScheduleRepository scheduleRepository;
    private final UserRepository userRepository;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Transactional(readOnly = true)
    public List<BroadcastScheduleResponse> getTodaySchedules() {
        LocalDate today = LocalDate.now(KST);
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(7).atStartOfDay();
        return scheduleRepository.findByScheduledAtBetween(start, end).stream()
                .map(BroadcastScheduleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BroadcastScheduleResponse> getStreamerSchedules(Long streamerId) {
        LocalDateTime now = LocalDateTime.now(KST);
        LocalDateTime future = now.plusDays(30);
        return scheduleRepository.findByScheduledAtBetween(now, future).stream()
                .filter(s -> s.getStreamer().getId().equals(streamerId))
                .map(BroadcastScheduleResponse::from)
                .toList();
    }

    @Transactional
    public BroadcastScheduleResponse createSchedule(Long userId, BroadcastScheduleRequest request, String imageUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (user.getRole() != Role.STREAMER && user.getRole() != Role.ADMIN) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        BroadcastSchedule schedule = BroadcastSchedule.builder()
                .streamer(user)
                .title(request.title())
                .description(request.description())
                .imageUrl(imageUrl)
                .scheduledAt(request.scheduledAt())
                .build();

        BroadcastSchedule saved = scheduleRepository.save(schedule);
        return BroadcastScheduleResponse.from(saved);
    }

    @Transactional
    public void deleteSchedule(Long scheduleId, Long userId) {
        BroadcastSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("방송 예고를 찾을 수 없습니다"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 본인 또는 관리자만 삭제 가능
        if (!schedule.getStreamer().getId().equals(userId) && user.getRole() != Role.ADMIN) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        scheduleRepository.delete(schedule);
    }
}
