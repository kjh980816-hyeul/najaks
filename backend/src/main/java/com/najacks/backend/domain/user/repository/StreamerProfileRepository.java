package com.najacks.backend.domain.user.repository;

import com.najacks.backend.domain.user.entity.StreamerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StreamerProfileRepository extends JpaRepository<StreamerProfile, Long> {

    Optional<StreamerProfile> findByUserId(Long userId);
}
