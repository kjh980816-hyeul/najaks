package com.najacks.backend.domain.clip.repository;

import com.najacks.backend.domain.clip.entity.Clip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClipRepository extends JpaRepository<Clip, Long> {

    List<Clip> findByStreamerId(Long streamerId);

    long countByStreamerId(Long streamerId);

    List<Clip> findTop10ByOrderByViewCountDesc();
}
