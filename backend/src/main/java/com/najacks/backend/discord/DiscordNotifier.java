package com.najacks.backend.discord;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Discord webhook 전송. webhook URL이 비어있으면 로그만 찍고 무시.
 */
@Component
@Slf4j
public class DiscordNotifier {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient client = RestClient.builder().build();

    @Value("${app.discord.webhook.admin:}")
    private String adminWebhook;

    @Value("${app.discord.webhook.notice:}")
    private String noticeWebhook;

    public void sendAdmin(String title, String description, Integer color) {
        send(adminWebhook, title, description, color);
    }

    public void sendNotice(String title, String description, Integer color) {
        send(noticeWebhook, title, description, color);
    }

    public void sendNoticeWithUrl(String title, String description, String url, Integer color) {
        ObjectNode embed = buildEmbed(title, description, color);
        if (url != null && !url.isBlank()) embed.put("url", url);
        sendEmbed(noticeWebhook, embed);
    }

    public void sendAdminWithUrl(String title, String description, String url, Integer color) {
        ObjectNode embed = buildEmbed(title, description, color);
        if (url != null && !url.isBlank()) embed.put("url", url);
        sendEmbed(adminWebhook, embed);
    }

    private void send(String webhookUrl, String title, String description, Integer color) {
        sendEmbed(webhookUrl, buildEmbed(title, description, color));
    }

    private void sendEmbed(String webhookUrl, ObjectNode embed) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.info("[DiscordNotifier] 웹훅 URL 미설정 — 발송 생략: {}", embed.path("title").asText(""));
            return;
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode embeds = body.putArray("embeds");
            embeds.add(embed);
            client.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Discord 웹훅 발송 실패", e);
        }
    }

    private ObjectNode buildEmbed(String title, String description, Integer color) {
        ObjectNode embed = objectMapper.createObjectNode();
        if (title != null) embed.put("title", truncate(title, 256));
        if (description != null) embed.put("description", truncate(description, 4000));
        if (color != null) embed.put("color", color);
        embed.put("timestamp", java.time.OffsetDateTime.now().toString());
        return embed;
    }

    private String truncate(String s, int limit) {
        if (s == null) return null;
        return s.length() <= limit ? s : s.substring(0, limit);
    }
}
