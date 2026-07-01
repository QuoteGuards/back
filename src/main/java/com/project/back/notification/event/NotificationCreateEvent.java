package com.project.back.notification.event;

import com.project.back.notification.entity.NotificationRelatedType;
import com.project.back.notification.entity.NotificationType;

/**
 * 알림 생성 요청 이벤트.
 * 발행 도메인의 트랜잭션이 커밋된 뒤에만 알림이 생성되도록,
 * NotificationService가 AFTER_COMMIT 단계에서 이 이벤트를 처리한다.
 */
public record NotificationCreateEvent(
        Long userId,
        NotificationType type,
        String title,
        String message,
        NotificationRelatedType relatedType,
        Long relatedId
) {}
