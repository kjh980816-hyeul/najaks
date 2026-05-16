package com.najacks.backend.domain.chat.repository;

import com.najacks.backend.domain.chat.entity.StreamerPremiumFeature;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StreamerPremiumFeatureRepository extends JpaRepository<StreamerPremiumFeature, Long> {
}
