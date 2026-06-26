package com.project.back.domain.auth.ratelimit;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 비밀번호 재설정 요청 Rate Limiter (인메모리, IP+email 기준)
 *
 * <ul>
 *   <li>동일 IP+email 조합으로 60초 내 재요청을 차단한다.</li>
 *   <li>인메모리 구현이므로 서비스 재시작 시 초기화된다. MVP 수준에서 허용.</li>
 *   <li>프로덕션 전환 시 Redis 기반으로 교체를 권장한다.</li>
 *   <li>만료 엔트리는 {@code tryAcquire} 호출마다 기회적으로 정리하여 메모리 누수를 방지한다.</li>
 * </ul>
 */
@Component
public class PasswordResetRateLimiter {

    private static final long COOLDOWN_MS = 60_000L;

    /**
     * 만료 엔트리 정리 주기: 요청 N건마다 한 번씩 전체 스윕을 실행한다.
     * 공개 엔드포인트 특성상 잦은 스윕보다 기회적 정리가 적절하다.
     */
    private static final int SWEEP_INTERVAL = 100;

    // key: "ip:email(소문자)", value: 요청 허용 재개 시각(ms)
    private final ConcurrentHashMap<String, Long> cache = new ConcurrentHashMap<>();
    private final AtomicInteger callCount = new AtomicInteger(0);

    /**
     * 요청 허용 여부를 확인하고, 허용된 경우 쿨다운을 등록한다.
     *
     * @return {@code true}이면 요청 허용, {@code false}이면 쿨다운 중
     */
    public boolean tryAcquire(String ip, String email) {
        // 이메일 정규화 — Locale.ROOT로 터키어 등 특수 로케일의 대소문자 변환 오류 방지
        String key = ip + ":" + email.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        long expiresAt = now + COOLDOWN_MS;

        // 기존 쿨다운이 남아있으면 거부
        Long existing = cache.get(key);
        if (existing != null && now < existing) {
            return false;
        }

        // putIfAbsent + replace 조합으로 동시 요청 중 한 건만 통과
        Long prev = cache.putIfAbsent(key, expiresAt);
        boolean acquired;
        if (prev == null) {
            acquired = true; // 신규 등록 성공
        } else {
            // 이미 존재하는 경우 재확인 (다른 스레드가 이미 설정했을 수 있음)
            acquired = now >= prev && cache.replace(key, prev, expiresAt);
        }

        // 기회적 만료 엔트리 정리 — SWEEP_INTERVAL마다 한 번 실행
        if (callCount.incrementAndGet() % SWEEP_INTERVAL == 0) {
            sweepExpired(now);
        }

        return acquired;
    }

    /**
     * 만료된 캐시 엔트리를 제거한다.
     * ConcurrentHashMap의 entrySet 반복은 스냅샷이 아니므로 동시 수정에 안전하다.
     */
    private void sweepExpired(long now) {
        cache.entrySet().removeIf(entry -> now >= entry.getValue());
    }
}
