package com.najacks.backend.global.util;

import com.najacks.backend.auth.CustomUserPrincipal;
import com.najacks.backend.global.exception.CustomException;
import com.najacks.backend.global.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtil {

    private SecurityUtil() {
        // utility class
    }

    public static Long getCurrentUserId() {
        Authentication authentication = getAuthentication();
        Object principal = authentication.getPrincipal();

        if (principal instanceof CustomUserPrincipal cup) {
            return cup.getId();
        }

        // 마지막 fallback: name이 숫자라면 파싱 (방어적)
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException e) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
    }

    public static Authentication getCurrentUser() {
        return getAuthentication();
    }

    public static Long getCurrentUserIdOrNull() {
        try {
            return getCurrentUserId();
        } catch (Exception e) {
            return null;
        }
    }

    private static Authentication getAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
        return authentication;
    }
}
