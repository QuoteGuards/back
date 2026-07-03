package com.project.back.domain.quote.service;

import com.project.back.domain.approval.entity.ApprovalRequest;
import com.project.back.domain.approval.repository.ApprovalRequestRepository;
import com.project.back.domain.approval.repository.QuoteApprovalReasonRepository;
import com.project.back.domain.customer.repository.CustomerRepository;
import com.project.back.domain.discount.repository.DiscountPolicyRepository;
import com.project.back.domain.product.repository.ProductRepository;
import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.quote.repository.QuoteItemRepository;
import com.project.back.domain.quote.repository.QuoteRepository;
import com.project.back.domain.training.service.TrainingService;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.entity.UserRole;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.domain.user.service.UserStatsUpdateService;
import com.project.back.global.enums.QuoteStatus;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import com.project.back.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DisplayName("QuoteService 단위 테스트")
class QuoteServiceTest {

    private UserRepository userRepository;
    private QuoteRepository quoteRepository;
    private QuoteItemRepository quoteItemRepository;
    private QuoteApprovalReasonRepository approvalReasonRepository;
    private ApprovalRequestRepository approvalRequestRepository;
    private CustomerRepository customerRepository;
    private QuoteCalculationService calculationService;
    private ApprovalCheckService approvalCheckService;
    private TrainingService trainingService;
    private UserStatsUpdateService userStatsUpdateService;
    private DiscountPolicyRepository discountPolicyRepository;
    private ProductRepository productRepository;
    private NotificationService notificationService;
    private QuoteService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        quoteRepository = mock(QuoteRepository.class);
        quoteItemRepository = mock(QuoteItemRepository.class);
        approvalReasonRepository = mock(QuoteApprovalReasonRepository.class);
        approvalRequestRepository = mock(ApprovalRequestRepository.class);
        customerRepository = mock(CustomerRepository.class);
        calculationService = mock(QuoteCalculationService.class);
        approvalCheckService = mock(ApprovalCheckService.class);
        trainingService = mock(TrainingService.class);
        userStatsUpdateService = mock(UserStatsUpdateService.class);
        discountPolicyRepository = mock(DiscountPolicyRepository.class);
        productRepository = mock(ProductRepository.class);
        notificationService = mock(NotificationService.class);

        service = new QuoteService(
                userRepository,
                quoteRepository,
                quoteItemRepository,
                approvalReasonRepository,
                approvalRequestRepository,
                customerRepository,
                calculationService,
                approvalCheckService,
                trainingService,
                userStatsUpdateService,
                discountPolicyRepository,
                productRepository,
                notificationService
        );
    }

    @Nested
    @DisplayName("cancelQuote - 견적 취소")
    class CancelQuoteTests {

        @Test
        @DisplayName("작성자 본인이 취소하면 견적이 CANCELLED로 전환된다 (연결된 PENDING 승인 요청 없음)")
        void cancelQuote_owner_success_noPendingApprovalRequest() {
            User owner = mock(User.class);
            when(owner.getId()).thenReturn(1L);
            when(owner.getRole()).thenReturn(UserRole.SALES_STAFF);

            Quote quote = Quote.builder().id(100L).createdBy(owner).status(QuoteStatus.DRAFT).build();

            when(quoteRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(quote));
            when(quoteRepository.findByIdWithApprovalReasons(100L)).thenReturn(Optional.empty());
            when(approvalRequestRepository.findByQuote_IdAndStatus(100L, ApprovalRequest.ApprovalStatus.PENDING))
                    .thenReturn(Optional.empty());

            Quote result = service.cancelQuote(100L, owner);

            assertThat(result.getStatus()).isEqualTo(QuoteStatus.CANCELLED);
        }

        @Test
        @DisplayName("SUPER_ADMIN은 작성자가 아니어도 취소할 수 있다")
        void cancelQuote_superAdmin_success() {
            User owner = mock(User.class);
            when(owner.getId()).thenReturn(1L);

            User admin = mock(User.class);
            when(admin.getId()).thenReturn(99L);
            when(admin.getRole()).thenReturn(UserRole.SUPER_ADMIN);

            Quote quote = Quote.builder().id(100L).createdBy(owner).status(QuoteStatus.APPROVAL_PENDING).build();

            when(quoteRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(quote));
            when(quoteRepository.findByIdWithApprovalReasons(100L)).thenReturn(Optional.empty());
            when(approvalRequestRepository.findByQuote_IdAndStatus(100L, ApprovalRequest.ApprovalStatus.PENDING))
                    .thenReturn(Optional.empty());

            Quote result = service.cancelQuote(100L, admin);

            assertThat(result.getStatus()).isEqualTo(QuoteStatus.CANCELLED);
        }

        @Test
        @DisplayName("작성자 본인도 SUPER_ADMIN도 아니면 QUOTE_ACCESS_DENIED 예외가 발생하고 취소되지 않는다")
        void cancelQuote_notOwnerNotAdmin_throwsAccessDenied() {
            User owner = mock(User.class);
            when(owner.getId()).thenReturn(1L);

            User other = mock(User.class);
            when(other.getId()).thenReturn(2L);
            when(other.getRole()).thenReturn(UserRole.SALES_STAFF);

            Quote quote = Quote.builder().id(100L).createdBy(owner).status(QuoteStatus.DRAFT).build();

            when(quoteRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(quote));
            when(quoteRepository.findByIdWithApprovalReasons(100L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancelQuote(100L, other))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.QUOTE_ACCESS_DENIED);

            assertThat(quote.getStatus()).isEqualTo(QuoteStatus.DRAFT);
            verifyNoInteractions(approvalRequestRepository);
        }

        @Test
        @DisplayName("연결된 PENDING 승인 요청이 있으면 함께 CANCELLED로 전환된다")
        void cancelQuote_withPendingApprovalRequest_alsoCancelsIt() {
            User owner = mock(User.class);
            when(owner.getId()).thenReturn(1L);
            when(owner.getRole()).thenReturn(UserRole.SALES_STAFF);

            Quote quote = Quote.builder().id(100L).createdBy(owner).status(QuoteStatus.APPROVAL_PENDING).build();
            ApprovalRequest approvalRequest = ApprovalRequest.builder()
                    .id(10L)
                    .quote(quote)
                    .status(ApprovalRequest.ApprovalStatus.PENDING)
                    .build();

            when(quoteRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(quote));
            when(quoteRepository.findByIdWithApprovalReasons(100L)).thenReturn(Optional.empty());
            when(approvalRequestRepository.findByQuote_IdAndStatus(100L, ApprovalRequest.ApprovalStatus.PENDING))
                    .thenReturn(Optional.of(approvalRequest));

            service.cancelQuote(100L, owner);

            assertThat(quote.getStatus()).isEqualTo(QuoteStatus.CANCELLED);
            assertThat(approvalRequest.getStatus()).isEqualTo(ApprovalRequest.ApprovalStatus.CANCELLED);
            assertThat(approvalRequest.getProcessedAt()).isNotNull();
        }

        @Test
        @DisplayName("취소 불가 상태(SENT)의 견적을 취소하려 하면 QUOTE_NOT_CANCELLABLE 예외가 발생한다")
        void cancelQuote_notCancellableStatus_throwsException() {
            User owner = mock(User.class);
            when(owner.getId()).thenReturn(1L);
            when(owner.getRole()).thenReturn(UserRole.SALES_STAFF);

            Quote quote = Quote.builder().id(100L).createdBy(owner).status(QuoteStatus.SENT).build();

            when(quoteRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(quote));
            when(quoteRepository.findByIdWithApprovalReasons(100L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancelQuote(100L, owner))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.QUOTE_NOT_CANCELLABLE);
        }
    }
}
