package com.project.back.notification.service;

import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.notification.dto.response.NotificationResponse;
import com.project.back.notification.entity.Notification;
import com.project.back.notification.entity.NotificationRelatedType;
import com.project.back.notification.entity.NotificationType;
import com.project.back.notification.repository.NotificationRepository;
import com.project.back.notification.sse.NotificationSseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock UserRepository userRepository;
    @Mock NotificationSseService sseService;

    @InjectMocks NotificationService notificationService;

    @Test
    void create_알림을_저장하고_SSE로_전송한다() {
        // given
        Long userId = 1L;
        User user = User.builder().build();
        when(userRepository.getReferenceById(userId)).thenReturn(user);

        // when
        notificationService.create(
                userId,
                NotificationType.EMAIL_SENT,
                "견적서 발송 완료",
                "견적 Q-2026-0001 이메일을 발송했습니다.",
                NotificationRelatedType.QUOTE,
                10L);

        // then: DB 저장 검증
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(NotificationType.EMAIL_SENT);
        assertThat(saved.getTitle()).isEqualTo("견적서 발송 완료");
        assertThat(saved.getRelatedType()).isEqualTo(NotificationRelatedType.QUOTE);
        assertThat(saved.getRelatedId()).isEqualTo(10L);

        // then: SSE 전송 검증
        verify(sseService).sendNotification(eq(userId), any(NotificationResponse.class));
    }

    @Test
    void getUnreadCount_레포지토리_카운트를_위임한다() {
        when(notificationRepository.countByUserIdAndIsReadFalse(1L)).thenReturn(3L);

        assertThat(notificationService.getUnreadCount(1L)).isEqualTo(3L);
    }

    @Test
    void markAllAsRead_안읽은_알림을_모두_읽음처리한다() {
        Notification n1 = spy(Notification.builder().build());
        Notification n2 = spy(Notification.builder().build());
        when(notificationRepository.findByUserIdAndIsReadFalse(1L)).thenReturn(List.of(n1, n2));

        notificationService.markAllAsRead(1L);

        verify(n1).markAsRead();
        verify(n2).markAsRead();
    }
}
