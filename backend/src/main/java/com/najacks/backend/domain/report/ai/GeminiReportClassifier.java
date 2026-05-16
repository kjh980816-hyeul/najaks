package com.najacks.backend.domain.report.ai;

import com.najacks.backend.ai.GeminiClient;
import com.najacks.backend.domain.report.dto.AiClassificationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiReportClassifier {

    private final GeminiClient geminiClient;

    private static final String SYSTEM_PROMPT = """
            당신은 스트리머 팬 커뮤니티의 신고 내용을 분석하는 분류기입니다.
            반드시 다음 JSON 스키마로만 응답합니다. 다른 설명이나 코드블록 없이 JSON만 출력합니다.

            {
              "category": "ABUSE" | "SPAM" | "SEXUAL" | "FALSE_INFO" | "COPYRIGHT" | "ETC",
              "severity": 1~5 정수,
              "summary": "200자 이내 한국어 핵심 요약",
              "keywords": ["핵심", "키워드", "최대 5개"]
            }

            심각도 기준:
            - 5: 성착취, 자해 유도, 즉각적 위협
            - 4: 명백한 욕설·혐오발언, 개인정보 노출
            - 3: 반복 스팸, 도배, 저작권 침해 의심
            - 2: 경미한 분쟁, 주관적 불쾌감
            - 1: 오신고 가능성 높음
            """;

    public AiClassificationResult classify(String reportContent, Long reportId) {
        return geminiClient.generateJson(
                geminiClient.getDefaultModel(),
                SYSTEM_PROMPT,
                "신고 내용:\n" + reportContent,
                400,
                AiClassificationResult.class,
                "REPORT_CLASSIFY",
                "report:" + reportId
        );
    }
}
