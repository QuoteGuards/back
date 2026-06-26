package com.project.back.domain.auth.event;

/**
 * 비밀번호 재설정 이메일 발송 이벤트
 *
 * <p>트랜잭션 커밋 이후 {@code @TransactionalEventListener(AFTER_COMMIT)}로 처리된다.
 * 커밋 전 메일 발송으로 인한 "토큰 없는 유효 링크" 불일치를 방지한다.</p>
 */
public record PasswordResetEmailEvent(
        Long userId,
        String userName,
        String userEmail,
        String rawToken
) {
    public static PasswordResetEmailEvent of(Long userId, String userName, String userEmail, String rawToken) {
        return new PasswordResetEmailEvent(userId, userName, userEmail, rawToken);
    }
}
