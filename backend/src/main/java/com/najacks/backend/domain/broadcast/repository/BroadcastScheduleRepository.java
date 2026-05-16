package com.najacks.backend.domain.broadcast.repository;

import com.najacks.backend.domain.broadcast.entity.BroadcastSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface BroadcastScheduleRepository extends JpaRepository<BroadcastSchedule, Long> {

    List<BroadcastSchedule> findByScheduledAtBetween(LocalDateTime start, LocalDateTime end);
}
