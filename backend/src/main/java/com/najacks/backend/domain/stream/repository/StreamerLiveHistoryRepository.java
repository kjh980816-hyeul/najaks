package com.najacks.backend.domain.stream.repository;

import com.najacks.backend.domain.stream.entity.StreamerLiveHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StreamerLiveHistoryRepository extends JpaRepository<StreamerLiveHistory, Long> {
    Optional<StreamerLiveHistory> findFirstByStreamerNoAndEndedAtIsNullOrderByStartedAtDesc(Long streamerNo);

    /**
     * 같은 stream_id 로 중복 row 가 있을 때를 대비해 가장 최근 것 1개만 반환.
     * (과거 단순 findByStreamId 는 중복 시 NonUniqueResultException 을 던져 폴링이 전부 실패했음)
     */
    Optional<StreamerLiveHistory> findTopByStreamIdOrderByIdDesc(String streamId);

    default Optional<StreamerLiveHistory> findByStreamId(String streamId) {
        return findTopByStreamIdOrderByIdDesc(streamId);
    }
}
