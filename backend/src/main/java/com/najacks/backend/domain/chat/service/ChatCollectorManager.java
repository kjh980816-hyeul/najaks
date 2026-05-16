package com.najacks.backend.domain.chat.service;

import com.najacks.backend.domain.chat.chzzk.ChzzkChatSocket;
import com.najacks.backend.domain.chat.chzzk.ChzzkSessionClient;
import com.najacks.backend.domain.chat.crypto.TokenCryptor;
import com.najacks.backend.domain.chat.entity.StreamerPremiumFeature;
import com.najacks.backend.domain.chat.repository.StreamerPremiumFeatureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 스트리머별 치지직 채팅 세션 수명 관리.
 * 방송 시작 시 connect(streamerNo, streamId) / 종료 시 disconnect(streamerNo).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatCollectorManager {

    private static final int MAX_CONCURRENT = 10;

    private final ChzzkSessionClient sessionClient;
    private final StreamerPremiumFeatureRepository premiumRepo;
    private final TokenCryptor cryptor;
    private final ChatBufferService buffer;

    private final Map<Long, ActiveSession> active = new ConcurrentHashMap<>();

    public boolean connect(Long streamerNo, String streamId) {
        if (active.containsKey(streamerNo)) return true;
        if (active.size() >= MAX_CONCURRENT) {
            log.warn("동시 연결 한도 초과 streamerNo={}", streamerNo);
            return false;
        }

        StreamerPremiumFeature feature = premiumRepo.findById(streamerNo).orElse(null);
        if (feature == null || feature.getChzzkAccessTokenEnc() == null) {
            log.info("OAuth 토큰 없음 — 채팅 수집 스킵 streamerNo={}", streamerNo);
            return false;
        }

        String accessToken;
        try {
            accessToken = cryptor.decrypt(feature.getChzzkAccessTokenEnc());
        } catch (Exception e) {
            log.warn("OAuth 토큰 복호화 실패 streamerNo={}", streamerNo, e);
            return false;
        }

        try {
            String sessionUrl = sessionClient.issueUserSessionUrl(accessToken);
            buffer.register(streamerNo, streamId);

            ChzzkChatSocket socket = new ChzzkChatSocket(
                    sessionUrl,
                    sessionKey -> {
                        try {
                            sessionClient.subscribeChat(accessToken, sessionKey);
                            log.info("✅ 채팅 구독 성공 streamerNo={}", streamerNo);
                        } catch (Exception e) {
                            log.warn("채팅 구독 실패 streamerNo={}", streamerNo, e);
                        }
                    },
                    msg -> buffer.accept(streamerNo, msg),
                    () -> active.remove(streamerNo)
            );
            socket.connect();
            active.put(streamerNo, new ActiveSession(socket, accessToken));
            log.info("🎙 채팅 수집 시작 streamerNo={} streamId={}", streamerNo, streamId);
            return true;
        } catch (Exception e) {
            log.warn("채팅 세션 수립 실패 streamerNo={}", streamerNo, e);
            buffer.unregister(streamerNo);
            return false;
        }
    }

    public void disconnect(Long streamerNo) {
        ActiveSession s = active.remove(streamerNo);
        buffer.flushFinal(streamerNo);
        if (s == null) return;
        try {
            String key = s.socket.getSessionKey();
            if (key != null) sessionClient.unsubscribeChat(s.accessToken, key);
        } catch (Exception ignored) {}
        try {
            s.socket.close();
        } catch (Exception ignored) {}
        log.info("📴 채팅 수집 종료 streamerNo={}", streamerNo);
    }

    public boolean isActive(Long streamerNo) { return active.containsKey(streamerNo); }

    private record ActiveSession(ChzzkChatSocket socket, String accessToken) {}
}
