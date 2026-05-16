package com.najacks.backend.domain.chat.repository;

import com.najacks.backend.domain.chat.entity.ChatMinuteStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMinuteStatsRepository extends JpaRepository<ChatMinuteStats, Long> {
    List<ChatMinuteStats> findByStreamIdOrderByMinuteBucketAsc(String streamId);
}
