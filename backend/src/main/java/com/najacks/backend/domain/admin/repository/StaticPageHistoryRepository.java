package com.najacks.backend.domain.admin.repository;

import com.najacks.backend.domain.admin.entity.StaticPageHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaticPageHistoryRepository extends JpaRepository<StaticPageHistory, Long> {

    List<StaticPageHistory> findBySlugOrderByVersionDesc(String slug);

    Optional<StaticPageHistory> findBySlugAndVersion(String slug, Integer version);

    Optional<StaticPageHistory> findTopBySlugOrderByVersionDesc(String slug);

    boolean existsBySlug(String slug);
}
