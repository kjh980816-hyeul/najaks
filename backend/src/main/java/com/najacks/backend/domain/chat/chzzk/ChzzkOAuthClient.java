package com.najacks.backend.domain.chat.chzzk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@Slf4j
public class ChzzkOAuthClient {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.chzzk.client-id:}")
    private String clientId;

    @Value("${app.chzzk.client-secret:}")
    private String clientSecret;

    @Value("${app.chzzk.redirect-uri:}")
    private String redirectUri;

    @Value("${app.chzzk.base-url:https://openapi.chzzk.naver.com}")
    private String baseUrl;

    private static final String AUTHORIZE_BASE = "https://chzzk.naver.com/account-interlock";

    private RestClient restClient;

    private RestClient client() {
        if (restClient == null) restClient = RestClient.builder().baseUrl(baseUrl).build();
        return restClient;
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank()
                && redirectUri != null && !redirectUri.isBlank();
    }

    public String buildAuthorizeUrl(String state) {
        return UriComponentsBuilder.fromUriString(AUTHORIZE_BASE)
                .queryParam("clientId", clientId)
                .queryParam("redirectUri", redirectUri)
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    public TokenResponse exchangeCode(String code, String state) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("grantType", "authorization_code");
        body.put("clientId", clientId);
        body.put("clientSecret", clientSecret);
        body.put("code", code);
        body.put("state", state);
        return callTokenEndpoint(body);
    }

    public TokenResponse refresh(String refreshToken) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("grantType", "refresh_token");
        body.put("refreshToken", refreshToken);
        body.put("clientId", clientId);
        body.put("clientSecret", clientSecret);
        return callTokenEndpoint(body);
    }

    private TokenResponse callTokenEndpoint(ObjectNode body) {
        JsonNode resp = client().post()
                .uri("/auth/v1/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (resp == null) throw new IllegalStateException("치지직 토큰 응답이 비어있음");

        // 응답이 {code, message, content} 또는 직접 {accessToken,...} 형태일 수 있음
        JsonNode content = resp.has("content") ? resp.get("content") : resp;
        if (content == null || content.isNull()) {
            throw new IllegalStateException("치지직 토큰 응답에 content 없음: " + resp);
        }

        return TokenResponse.builder()
                .accessToken(text(content, "accessToken"))
                .refreshToken(text(content, "refreshToken"))
                .tokenType(text(content, "tokenType"))
                .expiresIn(parseLong(text(content, "expiresIn")))
                .scope(text(content, "scope"))
                .build();
    }

    /**
     * GET /open/v1/users/me — 로그인 유저 채널 정보 조회.
     * 필요 scope: "유저 정보 조회". 실패(403/미허용 scope) 시 null 반환.
     */
    public ChannelInfo fetchUserInfo(String accessToken) {
        try {
            JsonNode resp = client().get()
                    .uri("/open/v1/users/me")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
            if (resp == null) return null;
            JsonNode content = resp.has("content") ? resp.get("content") : resp;
            if (content == null || content.isNull()) return null;
            String channelId = text(content, "channelId");
            String channelName = text(content, "channelName");
            if (channelId == null || channelId.isBlank()) return null;
            return ChannelInfo.builder()
                    .channelId(channelId)
                    .channelName(channelName)
                    .build();
        } catch (Exception e) {
            log.warn("치지직 유저 정보 조회 실패: {}", e.getMessage());
            return null;
        }
    }

    private String text(JsonNode n, String key) {
        JsonNode v = n.get(key);
        return v == null || v.isNull() ? null : v.asText();
    }

    private long parseLong(String s) {
        if (s == null) return 0;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0; }
    }

    @Getter
    @Builder
    public static class TokenResponse {
        private final String accessToken;
        private final String refreshToken;
        private final String tokenType;
        private final long expiresIn;   // seconds
        private final String scope;
    }

    @Getter
    @Builder
    public static class ChannelInfo {
        private final String channelId;
        private final String channelName;
    }
}
