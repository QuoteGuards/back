package com.project.back.notification.service;

import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.notification.dto.response.NotificationResponse;
import com.project.back.notification.entity.Notification;
import com.project.back.notification.entity.NotificationRelatedType;
import com.project.back.notification.entity.NotificationType;
import com.project.back.notification.repository.NotificationRepository;
import com.project.back.notification.sse.NotificationSseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationSseService sseService;

    // 특정 사용자 알림 목록 조회
    public List<NotificationResponse> getNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    // 안 읽은 알림 개수 조회
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    /**
     * 알림 생성 + 실시간(SSE) 전송.
     * 다른 도메인(승인/이메일/만료 등)에서 이벤트 발생 시 호출한다.
     * 알림 저장 실패가 호출자의 본 작업을 롤백시키지 않도록 별도 트랜잭션으로 격리한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationResponse create(Long userId, NotificationType type, String title,
                                       String message, NotificationRelatedType relatedType, Long relatedId) {
        User user = userRepository.getReferenceById(userId);
        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .message(message)
                .relatedType(relatedType)
                .relatedId(relatedId)
                .build();
        notificationRepository.save(notification);

        NotificationResponse response = NotificationResponse.from(notification);
        sseService.sendNotification(userId, response);
        return response;
    }

    // 안 읽은 알림 전체 읽음 처리
    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.findByUserIdAndIsReadFalse(userId)
                .forEach(Notification::markAsRead);
    }

    // 알림 읽음 처리
    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다."));

        if (!notification.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("해당 알림에 접근할 수 없습니다.");
        }

        notification.markAsRead();
    }
}
