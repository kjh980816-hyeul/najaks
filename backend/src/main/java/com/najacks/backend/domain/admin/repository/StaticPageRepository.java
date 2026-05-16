package com.najacks.backend.domain.admin.repository;

import com.najacks.backend.domain.admin.entity.StaticPage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StaticPageRepository extends JpaRepository<StaticPage, Long> {

    Optional<StaticPage> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
