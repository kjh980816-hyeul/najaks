package com.najacks.backend.infra.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@najaks.co.kr}")
    private String fromAddress;

    @Value("${app.site.base-url:https://najaks.co.kr}")
    private String siteBaseUrl;

    public void sendPasswordResetEmail(String to, String nickname, String token) {
        String resetLink = siteBaseUrl + "/reset-password?token=" + token;
        String subject = "[NAJAKS] 비밀번호 재설정 안내";

        String html = """
                <div style="font-family:'Pretendard','Apple SD Gothic Neo',sans-serif;max-width:560px;margin:0 auto;padding:32px 24px;background:#ffffff;color:#1a1a2e;">
                  <h1 style="font-size:22px;font-weight:800;margin:0 0 8px;background:linear-gradient(135deg,#6c63ff,#ff6b9d);-webkit-background-clip:text;-webkit-text-fill-color:transparent;">NAJAKS</h1>
                  <p style="margin:0 0 24px;color:#666;font-size:14px;">스트리머 · 팬 커뮤니티</p>
                  <h2 style="font-size:18px;font-weight:700;margin:0 0 16px;">비밀번호 재설정 요청</h2>
                  <p style="font-size:14px;line-height:1.7;color:#333;">안녕하세요, <strong>%s</strong>님.</p>
                  <p style="font-size:14px;line-height:1.7;color:#333;">아래 버튼을 눌러 새 비밀번호를 설정해주세요. 이 링크는 <strong>30분</strong> 동안 유효합니다.</p>
                  <div style="text-align:center;margin:32px 0;">
                    <a href="%s" style="display:inline-block;padding:14px 32px;background:linear-gradient(135deg,#6c63ff,#ff6b9d);color:#ffffff;text-decoration:none;border-radius:10px;font-weight:700;font-size:15px;">비밀번호 재설정하기</a>
                  </div>
                  <p style="font-size:12px;color:#888;line-height:1.6;">버튼이 동작하지 않으면 아래 주소를 복사해서 브라우저에 붙여넣어 주세요.<br><span style="word-break:break-all;color:#6c63ff;">%s</span></p>
                  <hr style="border:none;border-top:1px solid #eee;margin:28px 0;" />
                  <p style="font-size:12px;color:#999;line-height:1.6;">본 메일은 비밀번호 재설정을 요청한 경우에만 발송됩니다. 요청하지 않았다면 이 메일을 무시해주세요. 계정 보안이 걱정되시면 Admin.Najaks@gmail.com 으로 연락주세요.</p>
                </div>
                """.formatted(
                        nickname == null ? "회원" : nickname,
                        resetLink,
                        resetLink
                );

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress, "NAJAKS");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Password reset email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}", to, e);
            throw new RuntimeException("이메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }
    }
}
