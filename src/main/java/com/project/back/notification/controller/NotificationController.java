package com.project.back.notification.controller;


import com.project.back.global.common.ApiResponse;
import com.project.back.notification.dto.response.NotificationResponse;
import com.project.back.notification.service.NotificationService;
import com.project.back.notification.sse.NotificationSseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationSseService sseService;

    // 실시간 알림 구독 (SSE)
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal Long userId) {
        return sseService.subscribe(userId);
    }

    // 알림 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotifications(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "알림 목록 조회 성공",
                notificationService.getNotifications(userId)
        ));
    }

    // 읽지 않은 알림 개수 조회
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "읽지 않은 알림 개수 조회 성공",
                notificationService.getUnreadCount(userId)
        ));
    }

    // 알림 읽음 처리
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long notificationId

    ) {
        notificationService.markAsRead(notificationId, userId);

        return ResponseEntity.ok(ApiResponse.success(
                "알림 읽음 처리 성공",
                null
        ));
    }

    // 알림 전체 읽음 처리
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(
            @AuthenticationPrincipal Long userId
    ) {
        notificationService.markAllAsRead(userId);

        return ResponseEntity.ok(ApiResponse.success(
                "전체 알림 읽음 처리 성공",
                null
        ));
    }
}
