package com.project.back.domain.approval.service;

import com.project.back.domain.approval.entity.ApprovalRequest;
import com.project.back.domain.approval.repository.ApprovalRequestRepository;
import com.project.back.domain.approval.repository.QuoteApprovalHistoryRepository;
import com.project.back.domain.approval.repository.QuoteApprovalReasonRepository;
import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.quote.repository.QuoteItemRepository;
import com.project.back.domain.quote.repository.QuoteRepository;
import com.project.back.domain.quote.service.ApprovalCheckService;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.entity.UserRole;
import com.project.back.domain.user.entity.UserStatus;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.domain.user.service.UserStatsUpdateService;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import com.project.back.notification.event.NotificationCreateEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("ApprovalService 단위 테스트")
class ApprovalServiceTest {

    private ApprovalRequestRepository approvalRequestRepository;
    private QuoteApprovalReasonRepository quoteApprovalReasonRepository;
    private QuoteApprovalHistoryRepository quoteApprovalHistoryRepository;
    private UserRepository userRepository;
    private QuoteRepository quoteRepository;
    private QuoteItemRepository quoteItemRepository;
    private ApprovalCheckService approvalCheckService;
    private UserStatsUpdateService userStatsUpdateService;
    private ApplicationEventPublisher eventPublisher;
    private ApprovalService service;

    @BeforeEach
    void setUp() {
        approvalRequestRepository = mock(ApprovalRequestRepository.class);
        quoteApprovalReasonRepository = mock(QuoteApprovalReasonRepository.class);
        quoteApprovalHistoryRepository = mock(QuoteApprovalHistoryRepository.class);
        userRepository = mock(UserRepository.class);
        quoteRepository = mock(QuoteRepository.class);
        quoteItemRepository = mock(QuoteItemRepository.class);
        approvalCheckService = mock(ApprovalCheckService.class);
        userStatsUpdateService = mock(UserStatsUpdateService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new ApprovalService(
                approvalRequestRepository,
                quoteApprovalReasonRepository,
                quoteApprovalHistoryRepository,
                userRepository,
                quoteRepository,
                quoteItemRepository,
                approvalCheckService,
                userStatsUpdateService,
                eventPublisher
        );
    }

    @Nested
    @DisplayName("approve - 승인 처리")
    class ApproveTests {

        @Test
        @DisplayName("경로의 quoteId와 승인 요청의 견적이 다르면 APPROVAL_QUOTE_MISMATCH 예외")
        void approve_quoteMismatch() {
            Quote quote = mock(Quote.class);
            when(quote.getId()).thenReturn(100L);

            ApprovalRequest approvalRequest = ApprovalRequest.builder()
                    .id(10L)
                    .quote(quote)
                    .status(ApprovalRequest.ApprovalStatus.PENDING)
                    .requestedAt(LocalDateTime.now())
                    .build();

            when(approvalRequestRepository.findByIdWithUsers(10L)).thenReturn(Optional.of(approvalRequest));

            // 요청 경로의 quoteId(999L)가 실제 승인 요청의 견적(100L)과 다름
            assertThatThrownBy(() -> service.approve(999L, 10L, 5L, "승인합니다"))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPROVAL_QUOTE_MISMATCH);

            verifyNoInteractions(userRepository);
            verifyNoInteractions(quoteRepository);
        }
    }

    @Nested
    @DisplayName("reject - 반려 처리")
    class RejectTests {

        @Test
        @DisplayName("경로의 quoteId와 승인 요청의 견적이 다르면 APPROVAL_QUOTE_MISMATCH 예외")
        void reject_quoteMismatch() {
            Quote quote = mock(Quote.class);
            when(quote.getId()).thenReturn(100L);

            ApprovalRequest approvalRequest = ApprovalRequest.builder()
                    .id(10L)
                    .quote(quote)
                    .status(ApprovalRequest.ApprovalStatus.PENDING)
                    .requestedAt(LocalDateTime.now())
                    .build();

            when(approvalRequestRepository.findByIdWithUsers(10L)).thenReturn(Optional.of(approvalRequest));

            // 요청 경로의 quoteId(999L)가 실제 승인 요청의 견적(100L)과 다름
            assertThatThrownBy(() -> service.reject(999L, 10L, 5L, "금액 오류"))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPROVAL_QUOTE_MISMATCH);

            verifyNoInteractions(userRepository);
            verifyNoInteractions(quoteRepository);
        }
    }

    @Nested
    @DisplayName("reRequest - 재요청")
    class ReRequestTests {

        @Test
        @DisplayName("재요청 성공 시 견적이 APPROVAL_PENDING으로 전이되고, 반려사유/AI요약 초기화, 승인권자 알림 발송")
        void reRequest_success() {
            User requester = mock(User.class);
            when(requester.getId()).thenReturn(1L);
            when(requester.getRole()).thenReturn(UserRole.SALES_STAFF);
            when(requester.getDepartment()).thenReturn("영업1팀");
            when(requester.getName()).thenReturn("홍길동");

            User manager = mock(User.class);
            when(manager.getId()).thenReturn(2L);

            Quote quote = mock(Quote.class);
            when(quote.getId()).thenReturn(100L);
            when(quote.getQuoteNumber()).thenReturn("Q-2026-001");
            when(quote.getApprovalReasons()).thenReturn(new java.util.ArrayList<>());

            ApprovalRequest approvalRequest = ApprovalRequest.builder()
                    .id(10L)
                    .quote(quote)
                    .requester(requester)
                    .status(ApprovalRequest.ApprovalStatus.REJECTED)
                    .rejectReason("금액 오류")
                    .aiRiskSummary("- 기존 AI 요약")
                    .requestCount(1)
                    .requestedAt(LocalDateTime.now().minusDays(3))
                    .build();

            when(approvalRequestRepository.findByIdWithUsers(10L)).thenReturn(Optional.of(approvalRequest));
            when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
            when(quoteRepository.findById(100L)).thenReturn(Optional.of(quote));
            when(userRepository.findByRoleAndStatus(UserRole.SUPER_ADMIN, UserStatus.ACTIVE))
                    .thenReturn(List.of());
            when(userRepository.findByRoleAndDepartmentAndStatus(UserRole.SALES_MANAGER, "영업1팀", UserStatus.ACTIVE))
                    .thenReturn(List.of(manager));
            when(quoteItemRepository.findByQuoteIdOrderBySortOrderAsc(100L)).thenReturn(List.of());
            when(approvalCheckService.check(any(), any(), any(), any()))
                    .thenReturn(List.of(com.project.back.global.enums.ApprovalReasonType.HIGH_AMOUNT));

            LocalDateTime before = LocalDateTime.now();
            ApprovalRequest result = service.reRequest(100L, 10L, 1L, "수정 완료했습니다");

            assertThat(result.getStatus()).isEqualTo(ApprovalRequest.ApprovalStatus.PENDING);
            assertThat(result.getRejectReason()).isNull();
            assertThat(result.getAiRiskSummary()).isNull();
            assertThat(result.getRequestedAt()).isAfterOrEqualTo(before);
            assertThat(result.getRequestCount()).isEqualTo(2);

            verify(quote, times(1)).complete(true);
            verify(quoteApprovalReasonRepository, times(1)).saveAll(anyList());
            verify(eventPublisher, times(1)).publishEvent(any(NotificationCreateEvent.class));
        }

        @Test
        @DisplayName("REJECTED 상태가 아니면 APPROVAL_NOT_REJECTED 예외")
        void reRequest_notRejected() {
            User requester = mock(User.class);
            when(requester.getId()).thenReturn(1L);

            Quote quote = mock(Quote.class);
            when(quote.getId()).thenReturn(100L);

            ApprovalRequest approvalRequest = ApprovalRequest.builder()
                    .id(10L)
                    .quote(quote)
                    .requester(requester)
                    .status(ApprovalRequest.ApprovalStatus.PENDING)
                    .requestedAt(LocalDateTime.now())
                    .build();

            when(approvalRequestRepository.findByIdWithUsers(10L)).thenReturn(Optional.of(approvalRequest));
            when(userRepository.findById(1L)).thenReturn(Optional.of(requester));

            assertThatThrownBy(() -> service.reRequest(100L, 10L, 1L, "재요청 사유"))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPROVAL_NOT_REJECTED);

            verifyNoInteractions(quoteRepository);
        }

        @Test
        @DisplayName("본인 요청이 아니면 APPROVAL_ACCESS_DENIED 예외")
        void reRequest_notOwner() {
            User requester = mock(User.class);
            when(requester.getId()).thenReturn(1L);

            User other = mock(User.class);
            when(other.getId()).thenReturn(2L);

            Quote quote = mock(Quote.class);
            when(quote.getId()).thenReturn(100L);

            ApprovalRequest approvalRequest = ApprovalRequest.builder()
                    .id(10L)
                    .quote(quote)
                    .requester(requester)
                    .status(ApprovalRequest.ApprovalStatus.REJECTED)
                    .requestedAt(LocalDateTime.now())
                    .build();

            when(approvalRequestRepository.findByIdWithUsers(10L)).thenReturn(Optional.of(approvalRequest));
            when(userRepository.findById(2L)).thenReturn(Optional.of(other));

            assertThatThrownBy(() -> service.reRequest(100L, 10L, 2L, "재요청 사유"))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPROVAL_ACCESS_DENIED);

            verifyNoInteractions(quoteRepository);
        }

        @Test
        @DisplayName("경로의 quoteId와 승인 요청의 견적이 다르면 APPROVAL_QUOTE_MISMATCH 예외")
        void reRequest_quoteMismatch() {
            User requester = mock(User.class);
            when(requester.getId()).thenReturn(1L);

            Quote quote = mock(Quote.class);
            when(quote.getId()).thenReturn(100L);

            ApprovalRequest approvalRequest = ApprovalRequest.builder()
                    .id(10L)
                    .quote(quote)
                    .requester(requester)
                    .status(ApprovalRequest.ApprovalStatus.REJECTED)
                    .requestedAt(LocalDateTime.now())
                    .build();

            when(approvalRequestRepository.findByIdWithUsers(10L)).thenReturn(Optional.of(approvalRequest));

            // 요청 경로의 quoteId(999L)가 실제 승인 요청의 견적(100L)과 다름
            assertThatThrownBy(() -> service.reRequest(999L, 10L, 1L, "재요청 사유"))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPROVAL_QUOTE_MISMATCH);

            verifyNoInteractions(quoteRepository);
        }
    }
}
