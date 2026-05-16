package com.najacks.backend.auth.controller;

import com.najacks.backend.auth.CustomUserPrincipal;
import com.najacks.backend.auth.dto.*;
import com.najacks.backend.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody TokenRefreshRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse.UserInfo> getCurrentUser(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        AuthResponse.UserInfo userInfo = authService.getCurrentUser(principal.getId());
        return ResponseEntity.ok(userInfo);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<java.util.Map<String, String>> forgotPassword(
            @RequestBody java.util.Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "이메일을 입력해주세요"));
        }
        authService.requestPasswordReset(email.trim());
        return ResponseEntity.ok(java.util.Map.of(
                "message", "입력하신 이메일로 비밀번호 재설정 안내를 보냈습니다. 메일함(스팸함 포함)을 확인해주세요."
        ));
    }

    @GetMapping("/reset-password/validate")
    public ResponseEntity<java.util.Map<String, Boolean>> validateResetToken(
            @RequestParam String token) {
        boolean valid = authService.validateResetToken(token);
        return ResponseEntity.ok(java.util.Map.of("valid", valid));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<java.util.Map<String, String>> resetPassword(
            @RequestBody java.util.Map<String, String> body) {
        String token = body.get("token");
        String newPassword = body.get("newPassword");
        if (token == null || token.isBlank() || newPassword == null) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "토큰과 새 비밀번호를 입력해주세요"));
        }
        authService.resetPassword(token, newPassword);
        return ResponseEntity.ok(java.util.Map.of("message", "비밀번호가 변경되었습니다. 새 비밀번호로 로그인해주세요."));
    }
}
