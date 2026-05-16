package com.najacks.backend.domain.stream.external;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class ChzzkApiClient {

    @Value("${app.chzzk.client-id:}")
    private String clientId;

    @Value("${app.chzzk.client-secret:}")
    private String clientSecret;

    @Value("${app.chzzk.base-url:https://openapi.chzzk.naver.com}")
    private String baseUrl;

    @Value("${app.chzzk.lives.max-pages:500}")
    private int maxPages;

    private RestClient client;

    private RestClient client() {
        if (client == null) client = RestClient.builder().baseUrl(baseUrl).build();
        return client;
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    /**
     * 공식 Open API — GET /open/v1/lives 를 페이지네이션으로 전체 순회해서
     * 현재 라이브 중인 채널들을 channelId 키로 맵에 담아 반환.
     * size=20 고정(API 최대치), page.next 따라 끝까지 순회.
     * 치지직 공식 문서상 per-channel live-detail 엔드포인트가 없어 이 방식이 유일한 공식 경로.
     */
    public Map<String, ChzzkLiveDetail> fetchLiveChannelMap() {
        Map<String, ChzzkLiveDetail> map = new HashMap<>();
        if (!isConfigured()) return map;

        String next = null;
        int pages = 0;
        int totalLives = 0;
        try {
            do {
                StringBuilder urlBuilder = new StringBuilder(baseUrl)
                        .append("/open/v1/lives?size=20");
                if (next != null && !next.isBlank()) {
                    urlBuilder.append("&next=")
                            .append(URLEncoder.encode(next, StandardCharsets.UTF_8));
                }
                URI uri = URI.create(urlBuilder.toString());
                log.debug("치지직 lives 요청 page={} uri={}", pages + 1, uri);

                ResponseEntity<JsonNode> entity = client().get()
                        .uri(uri)
                        .header("Client-Id", clientId)
                        .header("Client-Secret", clientSecret)
                        .retrieve()
                        .toEntity(JsonNode.class);

                JsonNode body = entity.getBody();
                if (body == null) break;
                JsonNode content = body.path("content");
                JsonNode data = content.path("data");
                if (data.isArray()) {
                    for (JsonNode live : data) {
                        String channelId = text(live.path("channel"), "channelId");
                        if (channelId == null || channelId.isBlank()) {
                            channelId = text(live, "channelId");
                        }
                        if (channelId == null || channelId.isBlank()) continue;
                        String channelName = text(live.path("channel"), "channelName");
                        if (channelName == null) channelName = text(live, "channelName");

                        map.put(channelId, ChzzkLiveDetail.builder()
                                .channelId(channelId)
                                .status("OPEN")
                                .liveId(text(live, "liveId"))
                                .liveTitle(text(live, "liveTitle"))
                                .liveCategoryValue(text(live, "liveCategoryValue"))
                                .concurrentUserCount(intOrNull(live, "concurrentUserCount"))
                                .openDate(text(live, "openDate"))
                                .build());
                        totalLives++;
                    }
                }

                JsonNode page = content.path("page");
                next = text(page, "next");
                pages++;
            } while (next != null && !next.isBlank() && pages < maxPages);
        } catch (Exception e) {
            log.warn("치지직 /open/v1/lives 순회 중 예외 pages={} (지금까지 수집={}) message={}",
                    pages, totalLives, e.getMessage());
        }

        log.info("치지직 라이브 목록 수집: pages={} lives={}", pages, totalLives);
        return map;
    }

    /**
     * GET /open/v1/channels?channelIds=... — 채널 상세(이미지 URL, 팔로워, 인증마크).
     * 응답이 비었거나 실패 시 null.
     */
    public ChannelDetail fetchChannelDetail(String channelId) {
        if (!isConfigured() || channelId == null || channelId.isBlank()) return null;
        try {
            String uri = baseUrl + "/open/v1/channels?channelIds="
                    + URLEncoder.encode(channelId, StandardCharsets.UTF_8);
            JsonNode resp = client().get()
                    .uri(URI.create(uri))
                    .header("Client-Id", clientId)
                    .header("Client-Secret", clientSecret)
                    .retrieve()
                    .body(JsonNode.class);
            if (resp == null) return null;
            JsonNode data = resp.path("content").path("data");
            if (!data.isArray() || data.isEmpty()) return null;
            JsonNode c = data.get(0);
            return ChannelDetail.builder()
                    .channelId(text(c, "channelId"))
                    .channelName(text(c, "channelName"))
                    .channelImageUrl(text(c, "channelImageUrl"))
                    .followerCount(intOrNull(c, "followerCount"))
                    .verifiedMark(c.path("verifiedMark").asBoolean(false))
                    .build();
        } catch (Exception e) {
            log.warn("치지직 채널 상세 조회 실패 channelId={} msg={}", channelId, e.getMessage());
            return null;
        }
    }

    @lombok.Getter
    @lombok.Builder
    public static class ChannelDetail {
        private final String channelId;
        private final String channelName;
        private final String channelImageUrl;
        private final Integer followerCount;
        private final boolean verifiedMark;
    }

    private String text(JsonNode n, String key) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        JsonNode v = n.get(key);
        return v == null || v.isNull() ? null : v.asText();
    }

    private Integer intOrNull(JsonNode n, String key) {
        JsonNode v = n.get(key);
        return v != null && v.isNumber() ? v.asInt() : null;
    }
}
