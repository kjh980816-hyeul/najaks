package com.najacks.backend.domain.content.repository;

import com.najacks.backend.domain.content.entity.Content;
import com.najacks.backend.domain.content.entity.ContentCategory;
import com.najacks.backend.domain.content.entity.ContentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ContentRepository extends JpaRepository<Content, Long> {

    List<Content> findByStatus(ContentStatus status);

    List<Content> findByStreamerId(Long streamerId);

    long countByStreamerId(Long streamerId);

    List<Content> findByStatusIn(List<ContentStatus> statuses);

    List<Content> findByStatusInAndCategory(List<ContentStatus> statuses, ContentCategory category);

    List<Content> findByEndDateIsNotNullAndEndDateBefore(LocalDateTime cutoff);
}
