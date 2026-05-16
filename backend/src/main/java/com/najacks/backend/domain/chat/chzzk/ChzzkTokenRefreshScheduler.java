package com.najacks.backend.domain.chat.chzzk;

import com.najacks.backend.domain.chat.crypto.TokenCryptor;
import com.najacks.backend.domain.chat.entity.StreamerPremiumFeature;
import com.najacks.backend.domain.chat.repository.StreamerPremiumFeatureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 치지직 OAuth 토큰 만료 1시간 전 자동 갱신.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChzzkTokenRefreshScheduler {

    private final StreamerPremiumFeatureRepository premiumRepo;
    private final ChzzkOAuthClient oauth;
    private final TokenCryptor cryptor;

    /** 15분마다 체크 */
    @Scheduled(fixedDelayString = "900000", initialDelay = 300000)
    @Transactional
    public void refreshExpiring() {
        if (!oauth.isConfigured()) return;
        List<StreamerPremiumFeature> all = premiumRepo.findAll();
        LocalDateTime threshold = LocalDateTime.now().plusHours(1);

        for (StreamerPremiumFeature f : all) {
            if (f.getChzzkRefreshTokenEnc() == null) continue;
            if (f.getChzzkTokenExpiresAt() == null) continue;
            if (f.getChzzkTokenExpiresAt().isAfter(threshold)) continue;

            try {
                String refresh = cryptor.decrypt(f.getChzzkRefreshTokenEnc());
                ChzzkOAuthClient.TokenResponse token = oauth.refresh(refresh);
                if (token.getAccessToken() == null) continue;
                f.setChzzkAccessTokenEnc(cryptor.encrypt(token.getAccessToken()));
                if (token.getRefreshToken() != null) {
                    f.setChzzkRefreshTokenEnc(cryptor.encrypt(token.getRefreshToken()));
                }
                if (token.getExpiresIn() > 0) {
                    f.setChzzkTokenExpiresAt(LocalDateTime.now().plusSeconds(token.getExpiresIn()));
                }
                premiumRepo.save(f);
                log.info("🔄 치지직 토큰 갱신 streamerNo={}", f.getStreamerNo());
            } catch (Exception e) {
                log.warn("치지직 토큰 갱신 실패 streamerNo={}", f.getStreamerNo(), e);
            }
        }
    }
}
