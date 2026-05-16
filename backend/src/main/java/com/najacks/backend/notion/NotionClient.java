package com.najacks.backend.notion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.najacks.backend.notion.exception.NotionApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class NotionClient {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.notion.token:}")
    private String token;

    @Value("${app.notion.version:2022-06-28}")
    private String version;

    @Value("${app.notion.base-url:https://api.notion.com}")
    private String baseUrl;

    private RestClient client;

    private RestClient client() {
        if (client == null) {
            client = RestClient.builder().baseUrl(baseUrl).build();
        }
        return client;
    }

    public boolean isConfigured() {
        return token != null && !token.isBlank();
    }

    /**
     * 페이지 생성. properties는 속성명 → 속성 JSON Map.
     * children은 본문 블록 리스트 (nullable).
     * 반환: 생성된 page id
     */
    public String createPage(String databaseId, Map<String, ObjectNode> properties, List<ObjectNode> children) {
        return createPage(databaseId, properties, children, null);
    }

    /**
     * 페이지 생성 (icon URL 지원). iconExternalUrl이 있으면 페이지 아이콘으로 설정.
     */
    public String createPage(String databaseId, Map<String, ObjectNode> properties,
                             List<ObjectNode> children, String iconExternalUrl) {
        if (!isConfigured()) {
            throw new NotionApiException("Notion 토큰이 설정되지 않음");
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.putObject("parent").put("database_id", databaseId);

        ObjectNode props = body.putObject("properties");
        properties.forEach(props::set);

        if (iconExternalUrl != null && !iconExternalUrl.isBlank()) {
            ObjectNode icon = body.putObject("icon");
            icon.put("type", "external");
            icon.putObject("external").put("url", iconExternalUrl);
        }

        if (children != null && !children.isEmpty()) {
            ArrayNode arr = body.putArray("children");
            children.forEach(arr::add);
        }

        try {
            JsonNode resp = client().post()
                    .uri("/v1/pages")
                    .header("Authorization", "Bearer " + token)
                    .header("Notion-Version", version)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (resp == null || resp.get("id") == null) {
                throw new NotionApiException("Notion 페이지 생성 응답에 id 없음");
            }
            return resp.get("id").asText();
        } catch (NotionApiException e) {
            throw e;
        } catch (Exception e) {
            throw new NotionApiException("Notion 페이지 생성 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 기존 페이지(또는 블록) 하위에 children 블록을 추가. Notion 은 한 번에 최대 100개.
     */
    public void appendBlockChildren(String blockId, List<ObjectNode> children) {
        if (!isConfigured() || children == null || children.isEmpty()) return;
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode arr = body.putArray("children");
        children.forEach(arr::add);
        try {
            client().patch()
                    .uri("/v1/blocks/" + blockId + "/children")
                    .header("Authorization", "Bearer " + token)
                    .header("Notion-Version", version)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            throw new NotionApiException("Notion 블록 children 추가 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 페이지 속성 업데이트 (icon 선택적 업데이트)
     */
    public void updatePageProperties(String pageId, Map<String, ObjectNode> properties) {
        updatePageProperties(pageId, properties, null);
    }

    public void updatePageProperties(String pageId, Map<String, ObjectNode> properties, String iconExternalUrl) {
        if (!isConfigured()) return;
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode props = body.putObject("properties");
        properties.forEach(props::set);
        if (iconExternalUrl != null && !iconExternalUrl.isBlank()) {
            ObjectNode icon = body.putObject("icon");
            icon.put("type", "external");
            icon.putObject("external").put("url", iconExternalUrl);
        }
        try {
            client().patch()
                    .uri("/v1/pages/" + pageId)
                    .header("Authorization", "Bearer " + token)
                    .header("Notion-Version", version)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            throw new NotionApiException("Notion 페이지 업데이트 실패: " + e.getMessage(), e);
        }
    }

    /** DB 내에서 number 속성값으로 기존 페이지 검색. 있으면 page id 반환, 없으면 null. */
    public String findPageIdByNumberProperty(String databaseId, String propertyName, long value) {
        if (!isConfigured()) return null;
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ObjectNode filter = body.putObject("filter");
            filter.put("property", propertyName);
            filter.putObject("number").put("equals", value);
            body.put("page_size", 5);

            JsonNode resp = client().post()
                    .uri("/v1/databases/" + databaseId + "/query")
                    .header("Authorization", "Bearer " + token)
                    .header("Notion-Version", version)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (resp != null && resp.get("results") != null && resp.get("results").isArray()
                    && !resp.get("results").isEmpty()) {
                JsonNode first = resp.get("results").get(0);
                return first != null && first.get("id") != null ? first.get("id").asText() : null;
            }
            return null;
        } catch (Exception e) {
            log.warn("Notion DB 중복 검색 실패 db={} prop={} value={}", databaseId, propertyName, value);
            return null;
        }
    }

    /**
     * DB 스키마 조회 — properties 맵 (이름 → 타입) 반환.
     * DB에 없는 속성 필터링용.
     */
    public java.util.Map<String, String> getDatabaseSchema(String databaseId) {
        if (!isConfigured()) return java.util.Map.of();
        try {
            JsonNode resp = client().get()
                    .uri("/v1/databases/" + databaseId)
                    .header("Authorization", "Bearer " + token)
                    .header("Notion-Version", version)
                    .retrieve()
                    .body(JsonNode.class);
            java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
            if (resp != null && resp.get("properties") != null) {
                JsonNode props = resp.get("properties");
                props.fieldNames().forEachRemaining(name -> {
                    JsonNode p = props.get(name);
                    String type = p.get("type") != null ? p.get("type").asText() : "";
                    result.put(name, type);
                });
            }
            return result;
        } catch (Exception e) {
            log.warn("Notion DB 스키마 조회 실패 dbId={} msg={}", databaseId, e.getMessage());
            return java.util.Map.of();
        }
    }

    /**
     * DB 쿼리 (필터·정렬 옵션). 반환: 페이지 목록 JsonNode 배열.
     */
    public List<JsonNode> queryDatabase(String databaseId, ObjectNode filter, ArrayNode sorts) {
        if (!isConfigured()) return List.of();
        ObjectNode body = objectMapper.createObjectNode();
        if (filter != null) body.set("filter", filter);
        if (sorts != null) body.set("sorts", sorts);

        try {
            JsonNode resp = client().post()
                    .uri("/v1/databases/" + databaseId + "/query")
                    .header("Authorization", "Bearer " + token)
                    .header("Notion-Version", version)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            List<JsonNode> pages = new ArrayList<>();
            if (resp != null && resp.get("results") != null && resp.get("results").isArray()) {
                resp.get("results").forEach(pages::add);
            }
            return pages;
        } catch (Exception e) {
            throw new NotionApiException("Notion DB 쿼리 실패: " + e.getMessage(), e);
        }
    }

    /* ======== 응답 속성 파싱 헬퍼 ======== */

    public static String getTitle(JsonNode page, String propName) {
        JsonNode p = page.path("properties").path(propName).path("title");
        return concatRichText(p);
    }

    public static String getRichText(JsonNode page, String propName) {
        JsonNode p = page.path("properties").path(propName).path("rich_text");
        return concatRichText(p);
    }

    public static Long getNumber(JsonNode page, String propName) {
        JsonNode p = page.path("properties").path(propName).path("number");
        return p.isNumber() ? p.asLong() : null;
    }

    public static String getSelect(JsonNode page, String propName) {
        JsonNode p = page.path("properties").path(propName).path("select").path("name");
        return p.isTextual() ? p.asText() : null;
    }

    public static String getStatus(JsonNode page, String propName) {
        JsonNode p = page.path("properties").path(propName).path("status").path("name");
        return p.isTextual() ? p.asText() : null;
    }

    public static Boolean getCheckbox(JsonNode page, String propName) {
        JsonNode p = page.path("properties").path(propName).path("checkbox");
        return p.isBoolean() ? p.asBoolean() : null;
    }

    public static String getPageId(JsonNode page) {
        JsonNode id = page.get("id");
        return id != null ? id.asText() : null;
    }

    public static String getLastEditedTime(JsonNode page) {
        JsonNode t = page.get("last_edited_time");
        return t != null ? t.asText() : null;
    }

    private static String concatRichText(JsonNode arr) {
        if (arr == null || !arr.isArray()) return null;
        StringBuilder sb = new StringBuilder();
        for (JsonNode item : arr) {
            JsonNode plain = item.get("plain_text");
            if (plain != null && plain.isTextual()) sb.append(plain.asText());
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
