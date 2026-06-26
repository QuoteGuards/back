package com.project.back.domain.auth.ratelimit;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 비밀번호 재설정 요청 Rate Limiter (인메모리, IP+email 기준)
 *
 * <ul>
 *   <li>동일 IP+email 조합으로 60초 내 재요청을 차단한다.</li>
 *   <li>인메모리 구현이므로 서비스 재시작 시 초기화된다. MVP 수준에서 허용.</li>
 *   <li>프로덕션 전환 시 Redis 기반으로 교체를 권장한다.</li>
 * </ul>
 */
@Component
public class PasswordResetRateLimiter {

    private static final long COOLDOWN_MS = 60_000L;

    // key: "ip:email(소문자)", value: 요청 허용 재개 시각(ms)
    private final ConcurrentHashMap<String, Long> cache = new ConcurrentHashMap<>();

    /**
     * 요청 허용 여부를 확인하고, 허용된 경우 쿨다운을 등록한다.
     *
     * @return {@code true}이면 요청 허용, {@code false}이면 쿨다운 중
     */
    public boolean tryAcquire(String ip, String email) {
        String key = ip + ":" + email.toLowerCase();
        long now = System.currentTimeMillis();
        long expiresAt = now + COOLDOWN_MS;

        // 기존 쿨다운이 남아있으면 거부
        Long existing = cache.get(key);
        if (existing != null && now < existing) {
            return false;
        }

        // putIfAbsent + replace 조합으로 동시 요청 중 한 건만 통과
        Long prev = cache.putIfAbsent(key, expiresAt);
        if (prev == null) {
            return true; // 신규 등록 성공
        }
        // 이미 존재하는 경우 재확인 (다른 스레드가 이미 설정했을 수 있음)
        return now >= prev && cache.replace(key, prev, expiresAt);
    }
}
