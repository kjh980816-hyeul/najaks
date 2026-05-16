package com.najacks.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.najacks.backend.ai.entity.AiOperationMetric;
import com.najacks.backend.ai.exception.AiApiException;
import com.najacks.backend.ai.exception.AiParsingException;
import com.najacks.backend.ai.repository.AiOperationMetricRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiClient {

    private final AiOperationMetricRepository metricsRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.gemini.api-key:}")
    private String apiKey;

    @Value("${app.gemini.base-url:https://generativelanguage.googleapis.com}")
    private String baseUrl;

    @Value("${app.gemini.default-model:gemini-2.5-flash}")
    private String defaultModel;

    @Value("${app.gemini.lite-model:gemini-2.5-flash-lite}")
    private String liteModel;

    private RestClient client;

    private RestClient client() {
        if (client == null) {
            client = RestClient.builder().baseUrl(baseUrl).build();
        }
        return client;
    }

    public String getDefaultModel() { return defaultModel; }
    public String getLiteModel() { return liteModel; }
    public boolean isConfigured() { return apiKey != null && !apiKey.isBlank(); }

    /**
     * Gemini generateContent 호출. 반환: 응답 텍스트.
     * operationType/targetRef는 메트릭 기록용. jsonMode=true면 responseMimeType을 application/json 으로 지정.
     */
    public String generateText(String model, String systemInstruction, String userContent,
                               int maxOutputTokens, boolean jsonMode,
                               String operationType, String targetRef) {
        if (!isConfigured()) {
            throw new AiApiException("Gemini API 키가 설정되지 않음");
        }
        String primary = (model == null || model.isBlank()) ? defaultModel : model;
        // 503/429 등 일시적 오류 대비: 1차 primary → 2초 대기 후 재시도 → lite 모델 폴백
        try {
            return callOnce(primary, systemInstruction, userContent, maxOutputTokens, jsonMode,
                    operationType, targetRef);
        } catch (AiApiException e1) {
            if (!isTransient(e1)) throw e1;
            try { Thread.sleep(2000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            try {
                log.info("Gemini 재시도 (동일 모델) op={} model={}", operationType, primary);
                return callOnce(primary, systemInstruction, userContent, maxOutputTokens, jsonMode,
                        operationType, targetRef);
            } catch (AiApiException e2) {
                if (!isTransient(e2) || primary.equalsIgnoreCase(liteModel)) throw e2;
                log.info("Gemini lite 모델 폴백 op={} from={} to={}", operationType, primary, liteModel);
                return callOnce(liteModel, systemInstruction, userContent, maxOutputTokens, jsonMode,
                        operationType, targetRef);
            }
        }
    }

    private String callOnce(String useModel, String systemInstruction, String userContent,
                            int maxOutputTokens, boolean jsonMode,
                            String operationType, String targetRef) {
        long startMs = System.currentTimeMillis();
        try {
            ObjectNode body = buildRequestBody(systemInstruction, userContent, maxOutputTokens, jsonMode);
            String path = "/v1beta/models/" + useModel + ":generateContent?key=" + apiKey;
            JsonNode resp = client().post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (resp == null) {
                throw new AiApiException("Gemini 응답이 비어있음");
            }

            String text = extractText(resp);
            Integer inTok = numberOrNull(resp, "usageMetadata", "promptTokenCount");
            Integer outTok = numberOrNull(resp, "usageMetadata", "candidatesTokenCount");

            logMetric(operationType, targetRef, useModel, inTok, outTok,
                    (int) (System.currentTimeMillis() - startMs), true, null);
            return text;
        } catch (AiApiException e) {
            logMetric(operationType, targetRef, useModel, null, null,
                    (int) (System.currentTimeMillis() - startMs), false, safeMessage(e));
            throw e;
        } catch (Exception e) {
            logMetric(operationType, targetRef, useModel, null, null,
                    (int) (System.currentTimeMillis() - startMs), false, safeMessage(e));
            throw new AiApiException("Gemini API 호출 실패: " + e.getMessage(), e);
        }
    }

    private boolean isTransient(Throwable t) {
        String m = t.getMessage();
        if (m == null) return false;
        return m.contains("503") || m.contains("429") || m.contains("500")
                || m.contains("UNAVAILABLE") || m.contains("high demand") || m.contains("overloaded");
    }

    /**
     * JSON 응답을 특정 타입으로 파싱. ```json 블록이 있으면 제거.
     */
    public <T> T generateJson(String model, String systemInstruction, String userContent,
                              int maxOutputTokens, Class<T> type,
                              String operationType, String targetRef) {
        String raw = generateText(model, systemInstruction, userContent, maxOutputTokens, true,
                operationType, targetRef);
        String cleaned = cleanJson(raw);
        try {
            return objectMapper.readValue(cleaned, type);
        } catch (Exception e) {
            log.warn("Gemini JSON 파싱 실패 op={} raw={}", operationType, cleaned);
            throw new AiParsingException("JSON 파싱 실패: " + cleaned, e);
        }
    }

    private ObjectNode buildRequestBody(String systemInstruction, String userContent,
                                        int maxOutputTokens, boolean jsonMode) {
        ObjectNode body = objectMapper.createObjectNode();
        if (systemInstruction != null && !systemInstruction.isBlank()) {
            ObjectNode sys = objectMapper.createObjectNode();
            ArrayNode sysParts = objectMapper.createArrayNode();
            sysParts.add(objectMapper.createObjectNode().put("text", systemInstruction));
            sys.set("parts", sysParts);
            body.set("systemInstruction", sys);
        }
        ArrayNode contents = objectMapper.createArrayNode();
        ObjectNode userTurn = objectMapper.createObjectNode();
        userTurn.put("role", "user");
        ArrayNode userParts = objectMapper.createArrayNode();
        userParts.add(objectMapper.createObjectNode().put("text", userContent));
        userTurn.set("parts", userParts);
        contents.add(userTurn);
        body.set("contents", contents);

        ObjectNode genConfig = objectMapper.createObjectNode();
        genConfig.put("maxOutputTokens", maxOutputTokens);
        genConfig.put("temperature", 0.3);
        if (jsonMode) {
            genConfig.put("responseMimeType", "application/json");
        }
        // Gemini 2.5 flash는 기본적으로 thinking 토큰을 maxOutputTokens 안에서 소비함.
        // 짧은 JSON 응답용이라 reasoning 비활성화해서 출력 버짓 전체를 본문에 사용.
        ObjectNode thinkingConfig = objectMapper.createObjectNode();
        thinkingConfig.put("thinkingBudget", 0);
        genConfig.set("thinkingConfig", thinkingConfig);
        body.set("generationConfig", genConfig);
        return body;
    }

    private String extractText(JsonNode resp) {
        JsonNode cands = resp.get("candidates");
        if (cands == null || !cands.isArray() || cands.isEmpty()) {
            throw new AiApiException("Gemini 응답에 candidates 없음: " + resp.toString());
        }
        JsonNode content = cands.get(0).get("content");
        if (content == null) {
            throw new AiApiException("Gemini 응답에 content 없음");
        }
        JsonNode parts = content.get("parts");
        if (parts == null || !parts.isArray() || parts.isEmpty()) {
            throw new AiApiException("Gemini 응답에 parts 없음");
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode p : parts) {
            JsonNode t = p.get("text");
            if (t != null && t.isTextual()) sb.append(t.asText());
        }
        return sb.toString();
    }

    private String cleanJson(String raw) {
        String s = raw.trim();
        s = s.replaceAll("(?s)^```json\\s*", "");
        s = s.replaceAll("(?s)^```\\s*", "");
        s = s.replaceAll("(?s)```\\s*$", "");
        return s.trim();
    }

    private Integer numberOrNull(JsonNode root, String... path) {
        JsonNode cur = root;
        for (String k : path) {
            if (cur == null) return null;
            cur = cur.get(k);
        }
        return cur != null && cur.isNumber() ? cur.asInt() : null;
    }

    private void logMetric(String op, String ref, String model,
                           Integer inTok, Integer outTok, int duration,
                           boolean success, String errorMessage) {
        try {
            metricsRepo.save(AiOperationMetric.builder()
                    .operationType(op)
                    .targetRef(ref)
                    .model(model)
                    .inputTokens(inTok)
                    .outputTokens(outTok)
                    .durationMs(duration)
                    .success(success)
                    .errorMessage(errorMessage)
                    .build());
        } catch (Exception e) {
            log.warn("AI 메트릭 기록 실패 op={}", op, e);
        }
    }

    private String safeMessage(Exception e) {
        String m = e.getMessage();
        if (m == null) return e.getClass().getSimpleName();
        return m.length() > 500 ? m.substring(0, 500) : m;
    }
}
