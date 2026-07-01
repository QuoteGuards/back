package com.project.back.notification.sse;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 구독 전용 단기 토큰 관리.
 * <p>EventSource는 Authorization 헤더를 실을 수 없어 토큰을 쿼리파라미터로 넘겨야 하는데,
 * 장기 JWT를 URL에 노출하면 로그/히스토리 유출 위험이 크다. 이를 피하기 위해
 * 인증된 요청으로 발급받는 짧은 수명(기본 60초)·1회성 불투명 토큰을 사용한다.
 */
@Service
public class SseTokenService {

    private static final long TTL_MILLIS = 60_000L; // 60초
    private static final SecureRandom RANDOM = new SecureRandom();

    private record Entry(Long userId, long expiresAt) {}

    private final Map<String, Entry> tokens = new ConcurrentHashMap<>();

    public String issue(Long userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.put(token, new Entry(userId, System.currentTimeMillis() + TTL_MILLIS));
        return token;
    }

    /**
     * 토큰을 검증하고 userId를 반환한다. 1회성 — 검증 시 즉시 제거한다.
     * 유효하지 않거나 만료된 경우 null.
     */
    public Long consume(String token) {
        if (token == null) return null;
        Entry entry = tokens.remove(token);
        if (entry == null || entry.expiresAt() < System.currentTimeMillis()) {
            return null;
        }
        return entry.userId();
    }

    // 만료 토큰 정리 (선택적 호출)
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        tokens.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
    }
}
