package com.najacks.backend.domain.admin.repository;

import com.najacks.backend.domain.admin.entity.ApplicationGuideImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApplicationGuideImageRepository extends JpaRepository<ApplicationGuideImage, Long> {

    Optional<ApplicationGuideImage> findByPlatform(String platform);

    void deleteByPlatform(String platform);
}
