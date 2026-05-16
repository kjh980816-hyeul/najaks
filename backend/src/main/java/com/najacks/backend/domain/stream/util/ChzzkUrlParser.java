package com.najacks.backend.domain.stream.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChzzkUrlParser {

    /** chzzk.naver.com 도메인 경로에서 마지막 hex 토큰을 channelId로 추출. */
    private static final Pattern PATTERN = Pattern.compile(
            "chzzk\\.naver\\.com/(?:live/|lives/)?([a-zA-Z0-9]{10,})");

    private ChzzkUrlParser() {}

    public static String extractChannelId(String chzzkUrl) {
        if (chzzkUrl == null || chzzkUrl.isBlank()) return null;
        String trimmed = chzzkUrl.trim().replaceAll("[?#].*$", "").replaceAll("/$", "");
        Matcher m = PATTERN.matcher(trimmed);
        if (m.find()) return m.group(1);
        // URL이 아닌 단순 channelId일 가능성도 고려
        if (trimmed.matches("[a-zA-Z0-9]{10,}")) return trimmed;
        return null;
    }
}
