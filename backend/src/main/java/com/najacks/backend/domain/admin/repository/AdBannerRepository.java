package com.najacks.backend.domain.admin.repository;

import com.najacks.backend.domain.admin.entity.AdBanner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AdBannerRepository extends JpaRepository<AdBanner, Long> {

    List<AdBanner> findByActiveTrueAndStartDateBeforeAndEndDateAfter(LocalDateTime now1, LocalDateTime now2);
}
