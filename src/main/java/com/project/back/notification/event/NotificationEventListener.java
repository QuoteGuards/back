package com.project.back.notification.event;

import com.project.back.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 알림 생성 이벤트를 발행 도메인의 트랜잭션 커밋 이후에 처리한다.
 * <p>리스너를 NotificationService와 <b>별도 빈</b>으로 두는 이유:
 * <ul>
 *   <li>NotificationService.create()는 @Transactional(REQUIRES_NEW)인데, 같은 클래스에서
 *       자기 호출하면 프록시를 우회해 트랜잭션이 적용되지 않는다(저장 실패).</li>
 *   <li>리스너 메서드에 직접 @Transactional을 붙이면 @TransactionalEventListener 제약에 걸린다.</li>
 * </ul>
 * 별도 빈에서 create()를 호출하면 프록시를 거쳐 REQUIRES_NEW가 정상 적용된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotificationCreate(NotificationCreateEvent event) {
        // 알림은 부가 작업이므로 저장/전송 실패가 이미 커밋된 본 작업에 영향을 주지 않도록 흡수한다.
        try {
            notificationService.create(
                    event.userId(),
                    event.type(),
                    event.title(),
                    event.message(),
                    event.relatedType(),
                    event.relatedId());
        } catch (Exception e) {
            log.warn("알림 생성 실패 - userId={}, type={}", event.userId(), event.type(), e);
        }
    }
}
