package com.najacks.backend.domain.stream.repository;

import com.najacks.backend.domain.stream.entity.LiveStatus;
import com.najacks.backend.domain.stream.entity.StreamerLiveState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StreamerLiveStateRepository extends JpaRepository<StreamerLiveState, Long> {
    List<StreamerLiveState> findByCurrentLiveStatus(LiveStatus status);
}
