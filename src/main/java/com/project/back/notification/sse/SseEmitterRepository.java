package com.project.back.notification.sse;

import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 사용자별 SSE 연결(SseEmitter)을 메모리에 보관한다.
 * 한 사용자가 여러 탭/기기로 접속할 수 있으므로 userId 당 여러 emitter를 허용한다.
 * (단일 인스턴스 기준. 다중 인스턴스로 확장 시 Redis Pub/Sub 등이 필요)
 */
@Repository
public class SseEmitterRepository {

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public void add(Long userId, SseEmitter emitter) {
        emitters.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>()).add(emitter);
    }

    public List<SseEmitter> get(Long userId) {
        return emitters.getOrDefault(userId, List.of());
    }

    public void remove(Long userId, SseEmitter emitter) {
        List<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) return;
        userEmitters.remove(emitter);
        if (userEmitters.isEmpty()) {
            emitters.remove(userId);
        }
    }

    /**
     * 하트비트 전송 등 전체 연결을 순회해야 할 때 사용. userId-emitter 쌍을 평탄화해 반환한다.
     * (CopyOnWriteArrayList 기반이라 순회 중 remove가 발생해도 안전하다.)
     */
    public List<Map.Entry<Long, SseEmitter>> getAllEntries() {
        List<Map.Entry<Long, SseEmitter>> result = new ArrayList<>();
        for (Map.Entry<Long, List<SseEmitter>> entry : emitters.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                result.add(Map.entry(entry.getKey(), emitter));
            }
        }
        return result;
    }
}
