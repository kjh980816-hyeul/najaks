package com.najacks.backend.domain.chat.chzzk;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OAuth 콜백 검증용 state 임시 저장소 (메모리, 10분 TTL).
 * state → streamerNo 매핑. 콜백에서 state를 제시하면 한 번만 소비.
 */
@Component
@Slf4j
public class OAuthStateStore {

    private static final long TTL_SECONDS = 600L;

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    public String issue(Long streamerNo) {
        String state = UUID.randomUUID().toString().replace("-", "");
        store.put(state, new Entry(streamerNo, Instant.now().plus(TTL_SECONDS, ChronoUnit.SECONDS)));
        cleanupExpired();
        return state;
    }

    /** state 소비. 유효하면 streamerNo 반환. 유효하지 않으면 null. */
    public Long consume(String state) {
        if (state == null) return null;
        Entry e = store.remove(state);
        if (e == null) return null;
        if (Instant.now().isAfter(e.expiresAt)) return null;
        return e.streamerNo;
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        store.entrySet().removeIf(en -> now.isAfter(en.getValue().expiresAt));
    }

    private record Entry(Long streamerNo, Instant expiresAt) {}
}
