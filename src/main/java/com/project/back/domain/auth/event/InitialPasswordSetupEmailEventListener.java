package com.project.back.domain.auth.event;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * 초기 비밀번호 설정 이메일 발송 이벤트 리스너
 *
 * <ul>
 *   <li>트랜잭션 커밋 이후 실행 — 커밋 전 발송·롤백 불일치 방지</li>
 *   <li>발송 실패는 로깅만 — 사용자 생성 트랜잭션 자체는 이미 커밋됨</li>
 *   <li>{@code @Async}로 SMTP I/O가 HTTP 응답 스레드를 점유하지 않도록 함</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InitialPasswordSetupEmailEventListener {

    private final JavaMailSender mailSender;

    @Value("${mail.from-address}")
    private String fromAddress;

    @Value("${mail.from-name:QuoteGuard}")
    private String fromName;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.initial-password-token-expiry-minutes:1440}")
    private int tokenExpiryMinutes;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleInitialPasswordSetupEmail(InitialPasswordSetupEmailEvent event) {
        String setupLink = frontendUrl + "/set-password?token=" + event.rawToken();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, false, StandardCharsets.UTF_8.name());

            helper.setFrom(fromAddress, fromName);
            helper.setTo(event.userEmail());
            helper.setSubject("[QuoteGuard] 초기 비밀번호를 설정해주세요");
            helper.setText(buildEmailBody(HtmlUtils.htmlEscape(event.userName()), setupLink), true);

            mailSender.send(message);
            log.info("초기 비밀번호 설정 이메일 발송 완료 - userId={}", event.userId());

        } catch (MessagingException | UnsupportedEncodingException | RuntimeException e) {
            log.error("초기 비밀번호 설정 이메일 발송 실패 - userId={}", event.userId(), e);
        }
    }

    private String buildEmailBody(String userName, String setupLink) {
        int expiryHours = tokenExpiryMinutes / 60;
        String expiryText = expiryHours >= 1
                ? expiryHours + "시간"
                : tokenExpiryMinutes + "분";

        return String.format("""
                <div style="font-family:sans-serif;max-width:480px;margin:0 auto;padding:32px 24px;color:#1f2937">
                  <h2 style="font-size:18px;font-weight:600;margin-bottom:8px">초기 비밀번호 설정 안내</h2>
                  <p style="font-size:14px;color:#374151;margin-bottom:24px">
                    안녕하세요, <strong>%s</strong> 님.<br>
                    QuoteGuard 계정이 생성되었습니다.<br>
                    아래 버튼을 클릭하여 비밀번호를 설정한 후 로그인하세요.<br>
                    링크는 <strong>%s</strong> 후 만료됩니다.
                  </p>
                  <a href="%s"
                     style="display:inline-block;padding:12px 24px;background-color:#2563eb;color:#ffffff;font-size:14px;font-weight:600;text-decoration:none;border-radius:6px">
                    비밀번호 설정하기
                  </a>
                  <p style="font-size:12px;color:#9ca3af;margin-top:24px">
                    버튼이 동작하지 않으면 아래 링크를 복사하여 브라우저에 붙여넣으세요.<br>
                    <a href="%s" style="color:#6b7280;word-break:break-all">%s</a>
                  </p>
                  <hr style="border:none;border-top:1px solid #e5e7eb;margin:24px 0">
                  <p style="font-size:12px;color:#9ca3af">
                    본인이 요청하지 않은 경우 이 이메일을 무시하시거나 관리자에게 문의해주세요.
                  </p>
                </div>
                """, userName, expiryText, setupLink, setupLink, setupLink);
    }
}
