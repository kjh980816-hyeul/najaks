package com.najacks.backend.domain.chat.repository;

import com.najacks.backend.domain.chat.entity.StreamAnalysisReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StreamAnalysisReportRepository extends JpaRepository<StreamAnalysisReport, Long> {
    Optional<StreamAnalysisReport> findByStreamId(String streamId);
    List<StreamAnalysisReport> findTop20ByStreamerNoOrderByCreatedAtDesc(Long streamerNo);
    StreamAnalysisReport findTopByStreamerNoOrderByIdDesc(Long streamerNo);
}
