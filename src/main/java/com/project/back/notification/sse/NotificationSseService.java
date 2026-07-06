package com.project.back.notification.sse;

import com.project.back.notification.dto.response.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationSseService {

    // SSE 연결 타임아웃 (1시간). 만료 시 클라이언트(EventSource)가 자동 재연결한다.
    private static final long TIMEOUT = 60L * 60 * 1000;

    private final SseEmitterRepository emitterRepository;

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT);
        emitterRepository.add(userId, emitter);

        emitter.onCompletion(() -> emitterRepository.remove(userId, emitter));
        emitter.onTimeout(() -> emitterRepository.remove(userId, emitter));
        emitter.onError(e -> emitterRepository.remove(userId, emitter));

        // 최초 연결 확인용 더미 이벤트 (프록시 연결 유지 + onopen 트리거)
        send(emitter, userId, "connect", "connected");
        return emitter;
    }

    public void sendNotification(Long userId, NotificationResponse notification) {
        for (SseEmitter emitter : emitterRepository.get(userId)) {
            send(emitter, userId, "notification", notification);
        }
    }

    // 리버스 프록시(nginx/Caddy 등)의 idle 타임아웃으로 연결이 끊기지 않도록
    // 실제 알림이 없는 동안에도 주기적으로 더미 이벤트를 보내 연결을 유지한다.
    @Scheduled(fixedRate = 20_000)
    public void sendHeartbeat() {
        for (Map.Entry<Long, SseEmitter> entry : emitterRepository.getAllEntries()) {
            send(entry.getValue(), entry.getKey(), "heartbeat", "ping");
        }
    }

    private void send(SseEmitter emitter, Long userId, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data));
        } catch (IOException e) {
            log.debug("SSE 전송 실패 - userId={}, event={}", userId, eventName);
            emitterRepository.remove(userId, emitter);
        }
    }
}
