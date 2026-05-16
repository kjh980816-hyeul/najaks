package com.najacks.backend.domain.report.repository;

import com.najacks.backend.domain.report.entity.Block;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BlockRepository extends JpaRepository<Block, Long> {

    List<Block> findByBlockerId(Long blockerId);

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);
}
