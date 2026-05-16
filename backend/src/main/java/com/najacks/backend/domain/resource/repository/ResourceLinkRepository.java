package com.najacks.backend.domain.resource.repository;

import com.najacks.backend.domain.resource.entity.ResourceLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResourceLinkRepository extends JpaRepository<ResourceLink, Long> {
    List<ResourceLink> findAllByOrderByCategoryAscCreatedAtDesc();
}
