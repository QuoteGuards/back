package com.project.back.domain.approval.service;

import com.project.back.domain.approval.dto.QuoteSnapshotDto;
import com.project.back.domain.approval.dto.response.ApprovalRequestDetailResponse;
import com.project.back.domain.approval.entity.ApprovalRequest;
import com.project.back.domain.approval.entity.QuoteApprovalHistory;
import com.project.back.domain.approval.repository.ApprovalRequestRepository;
import com.project.back.domain.approval.repository.QuoteApprovalHistoryRepository;
import com.project.back.domain.approval.repository.QuoteApprovalReasonRepository;
import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.quote.entity.QuoteItem;
import com.project.back.domain.quote.repository.QuoteItemRepository;
import com.project.back.domain.quote.repository.QuoteRepository;
import com.project.back.domain.quote.service.ApprovalCheckService;
import com.project.back.domain.training.service.TrainingService;
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
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    private TrainingService trainingService;
    private ObjectMapper objectMapper;
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
        trainingService = mock(TrainingService.class);
        // 스냅샷 직렬화/역직렬화를 실제로 검증해야 하므로 목이 아닌 실제 ObjectMapper 사용
        objectMapper = new ObjectMapper();
        // 교육 이수 게이트는 이 테스트 클래스의 관심사가 아니므로 기본은 통과로 설정
        when(trainingService.canReviewApproval(any())).thenReturn(true);
        service = new ApprovalService(
                approvalRequestRepository,
                quoteApprovalReasonRepository,
                quoteApprovalHistoryRepository,
                userRepository,
                quoteRepository,
                quoteItemRepository,
                approvalCheckService,
                userStatsUpdateService,
                eventPublisher,
                trainingService,
                objectMapper
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

        @Test
        @DisplayName("반려 성공 시 그 시점 견적 스냅샷을 이력에 저장한다")
        void reject_success_savesQuoteSnapshot() {
            User requester = mock(User.class);
            when(requester.getId()).thenReturn(1L);

            User approver = mock(User.class);
            when(approver.getId()).thenReturn(2L);
            when(approver.getRole()).thenReturn(UserRole.SUPER_ADMIN);

            QuoteItem item = mock(QuoteItem.class);
            when(item.getProductId()).thenReturn(1L);
            when(item.getProductName()).thenReturn("노트북");
            when(item.getQuantity()).thenReturn(BigDecimal.valueOf(2));
            when(item.getUnitPrice()).thenReturn(BigDecimal.valueOf(1_000_000));

            Quote quote = mock(Quote.class);
            when(quote.getId()).thenReturn(100L);
            when(quote.getQuoteNumber()).thenReturn("Q-2026-001");
            when(quote.getTotalAmount()).thenReturn(BigDecimal.valueOf(2_000_000));
            when(quote.getProfitRate()).thenReturn(BigDecimal.valueOf(20.0));
            when(quote.getSubtotal()).thenReturn(BigDecimal.valueOf(2_000_000));
            when(quote.getDiscountAmount()).thenReturn(BigDecimal.ZERO);
            when(quote.getCreatedBy()).thenReturn(requester);

            ApprovalRequest approvalRequest = ApprovalRequest.builder()
                    .id(10L)
                    .quote(quote)
                    .requester(requester)
                    .status(ApprovalRequest.ApprovalStatus.PENDING)
                    .requestedAt(LocalDateTime.now())
                    .build();

            when(approvalRequestRepository.findByIdWithUsers(10L)).thenReturn(Optional.of(approvalRequest));
            when(userRepository.findById(2L)).thenReturn(Optional.of(approver));
            when(quoteRepository.findById(100L)).thenReturn(Optional.of(quote));
            when(quoteItemRepository.findByQuoteIdWithDiscountPolicyOrderBySortOrderAsc(100L)).thenReturn(List.of(item));

            service.reject(100L, 10L, 2L, "금액 오류");

            ArgumentCaptor<QuoteApprovalHistory> captor = ArgumentCaptor.forClass(QuoteApprovalHistory.class);
            verify(quoteApprovalHistoryRepository).save(captor.capture());

            QuoteApprovalHistory saved = captor.getValue();
            assertThat(saved.getAction()).isEqualTo(QuoteApprovalHistory.ActionType.REJECTED);
            assertThat(saved.getQuoteSnapshot()).isNotNull().contains("2000000").contains("노트북");
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
            when(quoteItemRepository.findByQuoteIdWithDiscountPolicyOrderBySortOrderAsc(100L)).thenReturn(List.of());
            when(approvalCheckService.check(any(), any(), any()))
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
        @DisplayName("재요청 성공 시 그 시점 견적 스냅샷을 이력에 저장한다")
        void reRequest_success_savesQuoteSnapshot() {
            User requester = mock(User.class);
            when(requester.getId()).thenReturn(1L);
            when(requester.getRole()).thenReturn(UserRole.SALES_STAFF);
            when(requester.getDepartment()).thenReturn("영업1팀");
            when(requester.getName()).thenReturn("홍길동");

            QuoteItem item = mock(QuoteItem.class);
            when(item.getProductId()).thenReturn(1L);
            when(item.getProductName()).thenReturn("노트북");
            when(item.getQuantity()).thenReturn(BigDecimal.valueOf(3));
            when(item.getUnitPrice()).thenReturn(BigDecimal.valueOf(1_000_000));

            Quote quote = mock(Quote.class);
            when(quote.getId()).thenReturn(100L);
            when(quote.getQuoteNumber()).thenReturn("Q-2026-001");
            when(quote.getApprovalReasons()).thenReturn(new java.util.ArrayList<>());
            when(quote.getTotalAmount()).thenReturn(BigDecimal.valueOf(3_000_000));
            when(quote.getProfitRate()).thenReturn(BigDecimal.valueOf(22.0));
            when(quote.getSubtotal()).thenReturn(BigDecimal.valueOf(3_000_000));
            when(quote.getDiscountAmount()).thenReturn(BigDecimal.ZERO);

            ApprovalRequest approvalRequest = ApprovalRequest.builder()
                    .id(10L)
                    .quote(quote)
                    .requester(requester)
                    .status(ApprovalRequest.ApprovalStatus.REJECTED)
                    .requestCount(1)
                    .requestedAt(LocalDateTime.now().minusDays(1))
                    .build();

            when(approvalRequestRepository.findByIdWithUsers(10L)).thenReturn(Optional.of(approvalRequest));
            when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
            when(quoteRepository.findById(100L)).thenReturn(Optional.of(quote));
            when(userRepository.findByRoleAndStatus(UserRole.SUPER_ADMIN, UserStatus.ACTIVE))
                    .thenReturn(List.of());
            when(userRepository.findByRoleAndDepartmentAndStatus(UserRole.SALES_MANAGER, "영업1팀", UserStatus.ACTIVE))
                    .thenReturn(List.of());
            when(quoteItemRepository.findByQuoteIdWithDiscountPolicyOrderBySortOrderAsc(100L)).thenReturn(List.of(item));
            when(approvalCheckService.check(any(), any(), any())).thenReturn(List.of());

            service.reRequest(100L, 10L, 1L, "수정 완료했습니다");

            ArgumentCaptor<QuoteApprovalHistory> captor = ArgumentCaptor.forClass(QuoteApprovalHistory.class);
            verify(quoteApprovalHistoryRepository).save(captor.capture());

            QuoteApprovalHistory saved = captor.getValue();
            assertThat(saved.getAction()).isEqualTo(QuoteApprovalHistory.ActionType.RE_REQUESTED);
            assertThat(saved.getQuoteSnapshot()).isNotNull().contains("3000000").contains("노트북");
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

    @Nested
    @DisplayName("cancelRequest - 승인 요청 철회")
    class CancelRequestTests {

        @Test
        @DisplayName("요청자 본인이 PENDING 건을 철회하면 CANCELLED로 전이되고, 견적은 DRAFT로 돌아가며, 이력이 남는다")
        void cancelRequest_success() {
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
            when(quoteRepository.findById(100L)).thenReturn(Optional.of(quote));

            ApprovalRequest result = service.cancelRequest(100L, 10L, 1L);

            assertThat(result.getStatus()).isEqualTo(ApprovalRequest.ApprovalStatus.CANCELLED);
            verify(quote, times(1)).saveAsDraft();

            ArgumentCaptor<QuoteApprovalHistory> captor = ArgumentCaptor.forClass(QuoteApprovalHistory.class);
            verify(quoteApprovalHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getAction()).isEqualTo(QuoteApprovalHistory.ActionType.CANCELLED);
        }

        @Test
        @DisplayName("본인 요청이 아니면 APPROVAL_ACCESS_DENIED 예외")
        void cancelRequest_notOwner() {
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

            assertThatThrownBy(() -> service.cancelRequest(100L, 10L, 2L))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPROVAL_ACCESS_DENIED);

            verifyNoInteractions(quoteRepository);
        }

        @Test
        @DisplayName("PENDING 상태가 아니면 APPROVAL_NOT_PENDING 예외")
        void cancelRequest_notPending() {
            User requester = mock(User.class);
            when(requester.getId()).thenReturn(1L);

            Quote quote = mock(Quote.class);
            when(quote.getId()).thenReturn(100L);

            ApprovalRequest approvalRequest = ApprovalRequest.builder()
                    .id(10L)
                    .quote(quote)
                    .requester(requester)
                    .status(ApprovalRequest.ApprovalStatus.APPROVED)
                    .requestedAt(LocalDateTime.now())
                    .build();

            when(approvalRequestRepository.findByIdWithUsers(10L)).thenReturn(Optional.of(approvalRequest));

            assertThatThrownBy(() -> service.cancelRequest(100L, 10L, 1L))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPROVAL_NOT_PENDING);

            verifyNoInteractions(quoteRepository);
        }

        @Test
        @DisplayName("경로의 quoteId와 승인 요청의 견적이 다르면 APPROVAL_QUOTE_MISMATCH 예외")
        void cancelRequest_quoteMismatch() {
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

            assertThatThrownBy(() -> service.cancelRequest(999L, 10L, 1L))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPROVAL_QUOTE_MISMATCH);

            verifyNoInteractions(quoteRepository);
        }
    }

    @Nested
    @DisplayName("getApprovalDetail - 승인 상세 조회 (견적 금액 요약)")
    class GetApprovalDetailTests {

        @Test
        @DisplayName("응답의 금액/이익률/품목 정보가 Quote 값과 일치한다")
        void getApprovalDetail_includesQuoteAmountSummary() {
            User requester = mock(User.class);
            when(requester.getId()).thenReturn(1L);
            when(requester.getName()).thenReturn("홍길동");

            QuoteItem item = mock(QuoteItem.class);
            when(item.getProductName()).thenReturn("노트북");
            when(item.getQuantity()).thenReturn(BigDecimal.valueOf(2));
            when(item.getUnitPrice()).thenReturn(BigDecimal.valueOf(1_000_000));
            when(item.getLineTotal()).thenReturn(BigDecimal.valueOf(2_000_000));

            Quote quote = mock(Quote.class);
            when(quote.getId()).thenReturn(100L);
            when(quote.getTotalAmount()).thenReturn(BigDecimal.valueOf(6_140_872));
            when(quote.getTotalCostAmount()).thenReturn(BigDecimal.valueOf(4_820_000));
            when(quote.getExpectedProfitAmount()).thenReturn(BigDecimal.valueOf(1_320_872));
            when(quote.getProfitRate()).thenReturn(BigDecimal.valueOf(21.5));
            when(quote.getItems()).thenReturn(List.of(item));

            ApprovalRequest approvalRequest = ApprovalRequest.builder()
                    .id(10L)
                    .quote(quote)
                    .requester(requester)
                    .status(ApprovalRequest.ApprovalStatus.PENDING)
                    .requestCount(1)
                    .requestedAt(LocalDateTime.now())
                    .build();

            when(approvalRequestRepository.findByIdWithUsers(10L)).thenReturn(Optional.of(approvalRequest));
            when(quoteRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(quote));
            when(quoteApprovalReasonRepository.findByQuote_Id(100L)).thenReturn(List.of());
            when(quoteApprovalHistoryRepository.findByApprovalRequestIdOrderByActedAtAsc(10L)).thenReturn(List.of());

            ApprovalRequestDetailResponse result = service.getApprovalDetail(10L);

            assertThat(result.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(6_140_872));
            assertThat(result.getTotalCostAmount()).isEqualByComparingTo(BigDecimal.valueOf(4_820_000));
            assertThat(result.getExpectedProfitAmount()).isEqualByComparingTo(BigDecimal.valueOf(1_320_872));
            assertThat(result.getProfitRate()).isEqualByComparingTo(BigDecimal.valueOf(21.5));
            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).getProductName()).isEqualTo("노트북");
            assertThat(result.getItems().get(0).getLineTotal()).isEqualByComparingTo(BigDecimal.valueOf(2_000_000));
        }

        @Test
        @DisplayName("반려 이력이 없으면 quoteDiff는 null이다")
        void getApprovalDetail_noRejectionHistory_quoteDiffIsNull() {
            User requester = mock(User.class);
            when(requester.getId()).thenReturn(1L);
            when(requester.getName()).thenReturn("홍길동");

            Quote quote = mock(Quote.class);
            when(quote.getId()).thenReturn(100L);
            when(quote.getItems()).thenReturn(List.of());

            ApprovalRequest approvalRequest = ApprovalRequest.builder()
                    .id(10L).quote(quote).requester(requester)
                    .status(ApprovalRequest.ApprovalStatus.PENDING)
                    .requestCount(1).requestedAt(LocalDateTime.now())
                    .build();

            QuoteApprovalHistory requested = QuoteApprovalHistory.builder()
                    .id(1L).approvalRequest(approvalRequest).actor(requester)
                    .action(QuoteApprovalHistory.ActionType.REQUESTED)
                    .afterStatus(ApprovalRequest.ApprovalStatus.PENDING)
                    .actedAt(LocalDateTime.now())
                    .build();

            when(approvalRequestRepository.findByIdWithUsers(10L)).thenReturn(Optional.of(approvalRequest));
            when(quoteRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(quote));
            when(quoteApprovalReasonRepository.findByQuote_Id(100L)).thenReturn(List.of());
            when(quoteApprovalHistoryRepository.findByApprovalRequestIdOrderByActedAtAsc(10L))
                    .thenReturn(List.of(requested));

            ApprovalRequestDetailResponse result = service.getApprovalDetail(10L);

            assertThat(result.getQuoteDiff()).isNull();
        }

        @Test
        @DisplayName("반려는 됐지만 아직 재요청 전이면 quoteDiff는 null이다")
        void getApprovalDetail_rejectedNotYetReRequested_quoteDiffIsNull() {
            User requester = mock(User.class);
            when(requester.getId()).thenReturn(1L);
            when(requester.getName()).thenReturn("홍길동");

            Quote quote = mock(Quote.class);
            when(quote.getId()).thenReturn(100L);
            when(quote.getItems()).thenReturn(List.of());

            ApprovalRequest approvalRequest = ApprovalRequest.builder()
                    .id(10L).quote(quote).requester(requester)
                    .status(ApprovalRequest.ApprovalStatus.REJECTED)
                    .requestCount(1).requestedAt(LocalDateTime.now())
                    .build();

            QuoteApprovalHistory rejected = QuoteApprovalHistory.builder()
                    .id(2L).approvalRequest(approvalRequest).actor(requester)
                    .action(QuoteApprovalHistory.ActionType.REJECTED)
                    .quoteSnapshot("{\"totalAmount\":1000000}")
                    .actedAt(LocalDateTime.now())
                    .build();

            when(approvalRequestRepository.findByIdWithUsers(10L)).thenReturn(Optional.of(approvalRequest));
            when(quoteRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(quote));
            when(quoteApprovalReasonRepository.findByQuote_Id(100L)).thenReturn(List.of());
            when(quoteApprovalHistoryRepository.findByApprovalRequestIdOrderByActedAtAsc(10L))
                    .thenReturn(List.of(rejected));

            ApprovalRequestDetailResponse result = service.getApprovalDetail(10L);

            assertThat(result.getQuoteDiff()).isNull();
        }

        @Test
        @DisplayName("반려 후 재요청되면 두 시점 스냅샷을 비교한 quoteDiff를 반환한다")
        void getApprovalDetail_rejectedThenReRequested_returnsQuoteDiff() {
            User requester = mock(User.class);
            when(requester.getId()).thenReturn(1L);
            when(requester.getName()).thenReturn("홍길동");

            Quote quote = mock(Quote.class);
            when(quote.getId()).thenReturn(100L);
            when(quote.getItems()).thenReturn(List.of());

            ApprovalRequest approvalRequest = ApprovalRequest.builder()
                    .id(10L).quote(quote).requester(requester)
                    .status(ApprovalRequest.ApprovalStatus.PENDING)
                    .requestCount(2).requestedAt(LocalDateTime.now())
                    .build();

            QuoteSnapshotDto beforeSnapshot = QuoteSnapshotDto.builder()
                    .totalAmount(BigDecimal.valueOf(1_000_000))
                    .profitRate(BigDecimal.valueOf(10))
                    .discountRate(BigDecimal.ZERO)
                    .items(List.of(QuoteSnapshotDto.ItemSnapshot.builder()
                            .productId(1L).productName("노트북")
                            .quantity(BigDecimal.ONE).unitPrice(BigDecimal.valueOf(1_000_000))
                            .build()))
                    .build();
            QuoteSnapshotDto afterSnapshot = QuoteSnapshotDto.builder()
                    .totalAmount(BigDecimal.valueOf(2_000_000))
                    .profitRate(BigDecimal.valueOf(15))
                    .discountRate(BigDecimal.ZERO)
                    .items(List.of(QuoteSnapshotDto.ItemSnapshot.builder()
                            .productId(1L).productName("노트북")
                            .quantity(BigDecimal.valueOf(2)).unitPrice(BigDecimal.valueOf(1_000_000))
                            .build()))
                    .build();

            QuoteApprovalHistory rejected = QuoteApprovalHistory.builder()
                    .id(2L).approvalRequest(approvalRequest).actor(requester)
                    .action(QuoteApprovalHistory.ActionType.REJECTED)
                    .quoteSnapshot(objectMapper.writeValueAsString(beforeSnapshot))
                    .actedAt(LocalDateTime.now().minusHours(2))
                    .build();
            QuoteApprovalHistory reRequested = QuoteApprovalHistory.builder()
                    .id(3L).approvalRequest(approvalRequest).actor(requester)
                    .action(QuoteApprovalHistory.ActionType.RE_REQUESTED)
                    .quoteSnapshot(objectMapper.writeValueAsString(afterSnapshot))
                    .actedAt(LocalDateTime.now().minusHours(1))
                    .build();

            when(approvalRequestRepository.findByIdWithUsers(10L)).thenReturn(Optional.of(approvalRequest));
            when(quoteRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(quote));
            when(quoteApprovalReasonRepository.findByQuote_Id(100L)).thenReturn(List.of());
            when(quoteApprovalHistoryRepository.findByApprovalRequestIdOrderByActedAtAsc(10L))
                    .thenReturn(List.of(rejected, reRequested));

            ApprovalRequestDetailResponse result = service.getApprovalDetail(10L);

            assertThat(result.getQuoteDiff()).isNotNull();
            assertThat(result.getQuoteDiff().getTotalAmountBefore()).isEqualByComparingTo(BigDecimal.valueOf(1_000_000));
            assertThat(result.getQuoteDiff().getTotalAmountAfter()).isEqualByComparingTo(BigDecimal.valueOf(2_000_000));
            assertThat(result.getQuoteDiff().getQuantityChangedItems()).hasSize(1);
            assertThat(result.getQuoteDiff().getQuantityChangedItems().get(0).getProductName()).isEqualTo("노트북");
        }
    }

    @Nested
    @DisplayName("getPendingList / getPendingListForManager - 처리 완료 목록 조회")
    class GetPendingListTests {

        @Test
        @DisplayName("파라미터 없이 호출하면(status=PENDING, 기간/처리자 없음) 기존 승인 대기 목록 조회와 동일하게 동작한다")
        void getPendingList_defaultParams_keepsLegacyBehavior() {
            ApprovalRequest pending = mock(ApprovalRequest.class);
            when(approvalRequestRepository.findByStatusOrderByRequestedAtAsc(ApprovalRequest.ApprovalStatus.PENDING))
                    .thenReturn(List.of(pending));

            List<ApprovalRequest> result = service.getPendingList(
                    ApprovalRequest.ApprovalStatus.PENDING, null, null, null);

            assertThat(result).containsExactly(pending);
            verify(approvalRequestRepository).findByStatusOrderByRequestedAtAsc(ApprovalRequest.ApprovalStatus.PENDING);
            verify(approvalRequestRepository, never()).search(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("status=APPROVED로 조회하면 search 쿼리로 위임하고 그 결과를 반환한다")
        void getPendingList_statusApproved_usesSearchQuery() {
            ApprovalRequest approved = mock(ApprovalRequest.class);
            LocalDateTime from = LocalDateTime.of(2026, 7, 1, 0, 0);
            LocalDateTime to = LocalDateTime.of(2026, 8, 1, 0, 0);
            when(approvalRequestRepository.search(ApprovalRequest.ApprovalStatus.APPROVED, null, null, from, to))
                    .thenReturn(List.of(approved));

            List<ApprovalRequest> result = service.getPendingList(
                    ApprovalRequest.ApprovalStatus.APPROVED, from, to, null);

            assertThat(result).containsExactly(approved);
            verify(approvalRequestRepository).search(ApprovalRequest.ApprovalStatus.APPROVED, null, null, from, to);
        }

        @Test
        @DisplayName("approverId를 지정하면(status=REJECTED) 해당 처리자 조건으로 search 쿼리를 호출한다")
        void getPendingList_approverIdOnly_usesSearchQueryWithApprover() {
            LocalDateTime from = LocalDateTime.of(2026, 7, 1, 0, 0);
            LocalDateTime to = LocalDateTime.of(2026, 8, 1, 0, 0);
            when(approvalRequestRepository.search(ApprovalRequest.ApprovalStatus.REJECTED, null, 5L, from, to))
                    .thenReturn(List.of());

            service.getPendingList(ApprovalRequest.ApprovalStatus.REJECTED, from, to, 5L);

            verify(approvalRequestRepository).search(ApprovalRequest.ApprovalStatus.REJECTED, null, 5L, from, to);
        }

        @Test
        @DisplayName("status/from/to가 모두 비어있지 않은 확장 조회에서 from/to 미지정 시 이번 달 범위를 기본값으로 사용한다")
        void getPendingList_noPeriodGiven_defaultsToCurrentMonth() {
            when(approvalRequestRepository.search(eq(ApprovalRequest.ApprovalStatus.APPROVED), isNull(), isNull(), any(), any()))
                    .thenReturn(List.of());

            service.getPendingList(ApprovalRequest.ApprovalStatus.APPROVED, null, null, null);

            ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(approvalRequestRepository).search(
                    eq(ApprovalRequest.ApprovalStatus.APPROVED), isNull(), isNull(),
                    fromCaptor.capture(), toCaptor.capture());

            LocalDateTime expectedFrom = LocalDate.now().withDayOfMonth(1).atStartOfDay();
            assertThat(fromCaptor.getValue()).isEqualTo(expectedFrom);
            assertThat(toCaptor.getValue()).isEqualTo(expectedFrom.plusMonths(1));
        }

        @Test
        @DisplayName("SALES_MANAGER가 조회하면 자기 부서로 범위가 제한된 search 쿼리를 호출한다 (타 부서 조건 미포함)")
        void getPendingListForManager_scopesToOwnDepartment() {
            User manager = mock(User.class);
            when(manager.getDepartment()).thenReturn("영업1팀");
            when(userRepository.findById(2L)).thenReturn(Optional.of(manager));

            LocalDateTime from = LocalDateTime.of(2026, 7, 1, 0, 0);
            LocalDateTime to = LocalDateTime.of(2026, 8, 1, 0, 0);
            when(approvalRequestRepository.search(ApprovalRequest.ApprovalStatus.APPROVED, "영업1팀", null, from, to))
                    .thenReturn(List.of());

            service.getPendingListForManager(2L, ApprovalRequest.ApprovalStatus.APPROVED, from, to, null);

            verify(approvalRequestRepository).search(ApprovalRequest.ApprovalStatus.APPROVED, "영업1팀", null, from, to);
            // 타 부서 조건이 섞이지 않는지 명시적으로 확인
            verify(approvalRequestRepository, never()).search(any(), eq("영업2팀"), any(), any(), any());
        }

        @Test
        @DisplayName("SALES_MANAGER가 파라미터 없이 조회하면 기존 부서별 대기 목록 조회와 동일하게 동작한다")
        void getPendingListForManager_defaultParams_keepsLegacyBehavior() {
            User manager = mock(User.class);
            when(manager.getDepartment()).thenReturn("영업1팀");
            when(userRepository.findById(2L)).thenReturn(Optional.of(manager));

            ApprovalRequest pending = mock(ApprovalRequest.class);
            when(approvalRequestRepository.findByStatusAndRequesterDepartment(
                    ApprovalRequest.ApprovalStatus.PENDING, "영업1팀"))
                    .thenReturn(List.of(pending));

            List<ApprovalRequest> result = service.getPendingListForManager(
                    2L, ApprovalRequest.ApprovalStatus.PENDING, null, null, null);

            assertThat(result).containsExactly(pending);
            verify(approvalRequestRepository, never()).search(any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("notifySlaBreaches - SLA 초과 승인 요청 알림")
    class NotifySlaBreachesTests {

        @Test
        @DisplayName("SLA를 초과한 건이 없으면 알림을 발행하지 않는다")
        void notifySlaBreaches_noTargets_doesNothing() {
            when(approvalRequestRepository.findPendingRequestedBefore(any())).thenReturn(List.of());

            service.notifySlaBreaches(2);

            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("요청자가 SALES_STAFF면 같은 부서 SALES_MANAGER 전원 + 전체 SUPER_ADMIN에게 알림을 발행한다")
        void notifySlaBreaches_staffRequester_notifiesDeptManagersAndAllAdmins() {
            User staff = User.builder()
                    .id(1L).name("홍길동").role(UserRole.SALES_STAFF).department("영업1팀")
                    .build();
            User manager = User.builder()
                    .id(2L).name("김관리").role(UserRole.SALES_MANAGER).department("영업1팀")
                    .build();
            User admin = User.builder()
                    .id(3L).name("박최고").role(UserRole.SUPER_ADMIN)
                    .build();

            Quote quote = mock(Quote.class);
            when(quote.getQuoteNumber()).thenReturn("Q-2026-0001");

            ApprovalRequest approvalRequest = ApprovalRequest.builder()
                    .id(10L).quote(quote).requester(staff)
                    .status(ApprovalRequest.ApprovalStatus.PENDING)
                    .requestedAt(LocalDateTime.now().minusDays(5))
                    .build();

            when(approvalRequestRepository.findPendingRequestedBefore(any()))
                    .thenReturn(List.of(approvalRequest));
            when(userRepository.findByRoleAndStatus(UserRole.SUPER_ADMIN, UserStatus.ACTIVE))
                    .thenReturn(List.of(admin));
            when(userRepository.findByRoleAndDepartmentAndStatus(UserRole.SALES_MANAGER, "영업1팀", UserStatus.ACTIVE))
                    .thenReturn(List.of(manager));

            service.notifySlaBreaches(2);

            ArgumentCaptor<NotificationCreateEvent> captor = ArgumentCaptor.forClass(NotificationCreateEvent.class);
            verify(eventPublisher, times(2)).publishEvent(captor.capture());

            List<Long> recipientIds = captor.getAllValues().stream()
                    .map(NotificationCreateEvent::userId).toList();
            assertThat(recipientIds).containsExactlyInAnyOrder(2L, 3L);

            NotificationCreateEvent event = captor.getAllValues().get(0);
            assertThat(event.type()).isEqualTo(com.project.back.notification.entity.NotificationType.APPROVAL_SLA_BREACH);
            assertThat(event.relatedType()).isEqualTo(com.project.back.notification.entity.NotificationRelatedType.APPROVAL);
            assertThat(event.relatedId()).isEqualTo(10L);
            assertThat(event.message()).contains("Q-2026-0001").contains("5일째");
        }

        @Test
        @DisplayName("요청자가 SALES_MANAGER면 전체 SUPER_ADMIN에게만 알림을 발행한다")
        void notifySlaBreaches_managerRequester_notifiesOnlyAdmins() {
            User managerRequester = User.builder()
                    .id(4L).name("김관리").role(UserRole.SALES_MANAGER).department("영업1팀")
                    .build();
            User admin = User.builder()
                    .id(3L).name("박최고").role(UserRole.SUPER_ADMIN)
                    .build();

            Quote quote = mock(Quote.class);
            when(quote.getQuoteNumber()).thenReturn("Q-2026-0002");

            ApprovalRequest approvalRequest = ApprovalRequest.builder()
                    .id(11L).quote(quote).requester(managerRequester)
                    .status(ApprovalRequest.ApprovalStatus.PENDING)
                    .requestedAt(LocalDateTime.now().minusDays(3))
                    .build();

            when(approvalRequestRepository.findPendingRequestedBefore(any()))
                    .thenReturn(List.of(approvalRequest));
            when(userRepository.findByRoleAndStatus(UserRole.SUPER_ADMIN, UserStatus.ACTIVE))
                    .thenReturn(List.of(admin));

            service.notifySlaBreaches(2);

            ArgumentCaptor<NotificationCreateEvent> captor = ArgumentCaptor.forClass(NotificationCreateEvent.class);
            verify(eventPublisher, times(1)).publishEvent(captor.capture());
            assertThat(captor.getValue().userId()).isEqualTo(3L);
            verify(userRepository, never())
                    .findByRoleAndDepartmentAndStatus(eq(UserRole.SALES_MANAGER), any(), any());
        }
    }
}
