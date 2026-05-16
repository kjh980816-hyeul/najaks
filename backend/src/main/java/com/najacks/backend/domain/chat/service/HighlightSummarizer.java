package com.najacks.backend.domain.chat.service;

import com.najacks.backend.ai.GeminiClient;
import com.najacks.backend.domain.chat.chzzk.ChzzkChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HighlightSummarizer {

    private final GeminiClient gemini;

    private static final String SYSTEM = """
            방송 채팅 급상승 순간을 분석합니다. 주어진 채팅 내용을 바탕으로
            "어떤 일이 일어났는지" 한국어 한 문장으로 추정 요약하세요.
            과장 없이 사실적으로. 100자 이내. 마크다운·코드블록 없이 순수 텍스트만.
            """;

    public String summarize(List<ChzzkChatMessage> messages) {
        StringBuilder sb = new StringBuilder("채팅 내용:\n");
        int limit = Math.min(messages.size(), 80);
        for (int i = 0; i < limit; i++) {
            sb.append("- ").append(messages.get(i).getContent()).append("\n");
        }
        return gemini.generateText(
                gemini.getLiteModel(),
                SYSTEM,
                sb.toString(),
                200,
                false,
                "HIGHLIGHT_SUMMARY",
                null
        ).trim();
    }
}
