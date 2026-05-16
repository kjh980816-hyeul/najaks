package com.najacks.backend.domain.chat.controller;

import com.najacks.backend.domain.chat.entity.EmailUnsubscribeToken;
import com.najacks.backend.domain.chat.repository.EmailUnsubscribeTokenRepository;
import com.najacks.backend.domain.chat.repository.StreamerPremiumFeatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
public class EmailUnsubscribeController {

    private final EmailUnsubscribeTokenRepository tokenRepo;
    private final StreamerPremiumFeatureRepository premiumRepo;

    @GetMapping(value = "/email/unsubscribe/{token}", produces = MediaType.TEXT_HTML_VALUE)
    @Transactional
    public ResponseEntity<String> unsubscribe(@PathVariable String token) {
        EmailUnsubscribeToken t = tokenRepo.findById(token).orElse(null);
        if (t == null) {
            return ResponseEntity.status(404).body(htmlBody("링크가 유효하지 않습니다.", false));
        }
        if (t.getUsedAt() == null) {
            t.setUsedAt(LocalDateTime.now());
            tokenRepo.save(t);
        }
        premiumRepo.findById(t.getStreamerNo()).ifPresent(f -> {
            f.setEmailEnabled(false);
            premiumRepo.save(f);
        });
        return ResponseEntity.ok(htmlBody("리포트 이메일 수신이 거부되었습니다.", true));
    }

    private String htmlBody(String message, boolean success) {
        String color = success ? "#22aa66" : "#e75555";
        return """
                <!doctype html>
                <html lang="ko"><head><meta charset="UTF-8"><title>나작스</title></head>
                <body style="font-family:'Apple SD Gothic Neo',sans-serif;background:#f4f5f7;padding:60px 20px;text-align:center;">
                  <div style="max-width:480px;margin:0 auto;background:#fff;padding:40px 32px;border-radius:12px;">
                    <div style="font-size:48px;">%s</div>
                    <h2 style="color:%s;margin:16px 0 8px;">%s</h2>
                    <p style="color:#666;margin:0;">나작스</p>
                    <a href="https://najaks.co.kr" style="display:inline-block;margin-top:24px;color:#5B8DEF;text-decoration:none;">→ 나작스 홈으로</a>
                  </div>
                </body></html>
                """.formatted(success ? "✅" : "⚠️", color, message);
    }
}
