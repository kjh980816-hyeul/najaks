package com.najacks.backend.domain.user.repository;

import com.najacks.backend.domain.user.entity.ApplicationStatus;
import com.najacks.backend.domain.user.entity.StreamerApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StreamerApplicationRepository extends JpaRepository<StreamerApplication, Long> {

    Optional<StreamerApplication> findByUserId(Long userId);

    List<StreamerApplication> findByStatus(ApplicationStatus status);
}
