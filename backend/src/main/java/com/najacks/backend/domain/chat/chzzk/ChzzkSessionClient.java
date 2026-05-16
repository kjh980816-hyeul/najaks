package com.najacks.backend.domain.chat.chzzk;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class ChzzkSessionClient {

    @Value("${app.chzzk.base-url:https://openapi.chzzk.naver.com}")
    private String baseUrl;

    private RestClient client;

    private RestClient client() {
        if (client == null) client = RestClient.builder().baseUrl(baseUrl).build();
        return client;
    }

    /** User 인증으로 세션 URL 발급 */
    public String issueUserSessionUrl(String accessToken) {
        JsonNode resp = client().get()
                .uri("/open/v1/sessions/auth")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(JsonNode.class);
        return extractUrl(resp);
    }

    /** CHAT 이벤트 구독 (sessionKey는 연결 직후 SYSTEM connected에서 받음) */
    public void subscribeChat(String accessToken, String sessionKey) {
        client().post()
                .uri(uri -> uri.path("/open/v1/sessions/events/subscribe/chat")
                        .queryParam("sessionKey", sessionKey).build())
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity();
    }

    public void unsubscribeChat(String accessToken, String sessionKey) {
        try {
            client().post()
                    .uri(uri -> uri.path("/open/v1/sessions/events/unsubscribe/chat")
                            .queryParam("sessionKey", sessionKey).build())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("채팅 구독 해제 실패 (무시): {}", e.getMessage());
        }
    }

    private String extractUrl(JsonNode resp) {
        if (resp == null) throw new IllegalStateException("치지직 세션 응답 없음");
        JsonNode content = resp.has("content") ? resp.get("content") : resp;
        JsonNode url = content != null ? content.get("url") : null;
        if (url == null || !url.isTextual()) {
            throw new IllegalStateException("치지직 세션 응답에 url 없음: " + resp);
        }
        return url.asText();
    }
}
