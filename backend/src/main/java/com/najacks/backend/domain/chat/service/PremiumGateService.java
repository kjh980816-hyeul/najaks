package com.najacks.backend.domain.chat.service;

import com.najacks.backend.domain.chat.entity.StreamerPremiumFeature;
import com.najacks.backend.domain.chat.repository.StreamerPremiumFeatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PremiumGateService {

    private final StreamerPremiumFeatureRepository repo;

    @Transactional(readOnly = true)
    public boolean isEnabled(Long streamerNo) {
        return repo.findById(streamerNo)
                .filter(f -> Boolean.TRUE.equals(f.getChatAnalysisEnabled()))
                .filter(f -> f.getExpiresAt() == null || f.getExpiresAt().isAfter(LocalDateTime.now()))
                .isPresent();
    }

    @Transactional(readOnly = true)
    public boolean isEmailEnabled(Long streamerNo) {
        return repo.findById(streamerNo)
                .filter(f -> Boolean.TRUE.equals(f.getEmailEnabled()))
                .isPresent();
    }

    @Transactional
    public StreamerPremiumFeature getOrCreate(Long streamerNo) {
        return repo.findById(streamerNo).orElseGet(() -> repo.save(
                StreamerPremiumFeature.builder().streamerNo(streamerNo).build()));
    }
}
