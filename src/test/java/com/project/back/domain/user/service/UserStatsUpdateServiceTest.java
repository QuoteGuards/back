package com.project.back.domain.user.service;

import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.quote.repository.QuoteRepository;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.entity.UserRole;
import com.project.back.domain.user.entity.UserStats;
import com.project.back.domain.user.entity.UserStatus;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.domain.user.repository.UserStatsRepository;
import com.project.back.global.enums.QuoteStatus;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserStatsUpdateServiceTest {

    @InjectMocks
    private UserStatsUpdateService userStatsUpdateService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserStatsRepository userStatsRepository;

    @Mock
    private QuoteRepository quoteRepository;

    // ── 헬퍼 ──────────────────────────────────────────────────────────────

    private User buildUser(Long id) throws Exception {
        User user = User.builder()
                .id(id).email("user" + id + "@test.com").password("encoded")
                .name("테스터" + id).department("영업1팀").position("대리")
                .phone("010-123" + id + "-5678").status(UserStatus.APPROVED)
                .role(UserRole.SALES_STAFF).build();
        setField(user, "createdAt", LocalDateTime.now());
        setField(user, "updatedAt", LocalDateTime.now());
        return user;
    }

    private Quote buildQuote(User owner, QuoteStatus status,
                             String totalAmount, String supplyAmount,
                             String profitAmount, String profitRate,
                             String subtotal, String discountAmount) throws Exception {
        Quote quote = Quote.builder()
                .createdBy(owner)
                .quoteNumber("Q20260101000001")
                .build();
        setField(quote, "status", status);
        setField(quote, "totalAmount", new BigDecimal(totalAmount));
        setField(quote, "supplyAmount", new BigDecimal(supplyAmount));
        setField(quote, "expectedProfitAmount", new BigDecimal(profitAmount));
        setField(quote, "profitRate", new BigDecimal(profitRate));
        setField(quote, "subtotal", new BigDecimal(subtotal));
        setField(quote, "discountAmount", new BigDecimal(discountAmount));
        setField(quote, "isLatest", true);
        return quote;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    // ── recalculate ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("recalculate(userId)")
    class Recalculate {

        @Test
        @DisplayName("제출된 견적이 있으면 통계를 올바르게 집계하여 저장한다")
        void success_aggregatesCorrectly() throws Exception {
            // given
            User user = buildUser(1L);
            // SENT 1건, APPROVED 1건, REJECTED 1건, APPROVAL_PENDING 1건
            Quote sent     = buildQuote(user, QuoteStatus.SENT,     "1100000", "1000000", "200000", "20.00", "1000000", "0");
            Quote approved = buildQuote(user, QuoteStatus.APPROVED,  "550000",  "500000", "100000", "20.00",  "500000", "0");
            Quote rejected = buildQuote(user, QuoteStatus.REJECTED,  "330000",  "300000",  "60000", "20.00",  "330000", "30000");
            Quote pending  = buildQuote(user, QuoteStatus.APPROVAL_PENDING, "220000", "200000", "40000", "20.00", "220000", "20000");

            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(quoteRepository.findSubmittedByUserId(anyLong(), anyList()))
                    .willReturn(List.of(sent, approved, rejected, pending));
            given(userStatsRepository.findByUserId(1L)).willReturn(Optional.empty());

            // when
            userStatsUpdateService.recalculate(1L);

            // then
            ArgumentCaptor<UserStats> captor = ArgumentCaptor.forClass(UserStats.class);
            verify(userStatsRepository).save(captor.capture());
            UserStats saved = captor.getValue();

            assertThat(saved.getTotalQuotes()).isEqualTo(4);
            assertThat(saved.getApprovedQuotes()).isEqualTo(2);  // SENT + APPROVED
            assertThat(saved.getRejectedQuotes()).isEqualTo(1);
            assertThat(saved.getSentQuotes()).isEqualTo(1);
            // 금액은 SENT + APPROVED(isApproved) 기준
            assertThat(saved.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1650000"));
            assertThat(saved.getTotalSupplyAmount()).isEqualByComparingTo(new BigDecimal("1500000"));
            assertThat(saved.getTotalProfitAmount()).isEqualByComparingTo(new BigDecimal("300000"));
        }

        @Test
        @DisplayName("기존 UserStats가 없으면 새로 생성하여 저장한다")
        void success_createsNewStats() throws Exception {
            // given
            User user = buildUser(2L);
            Quote q = buildQuote(user, QuoteStatus.SENT, "1000000", "900000", "180000", "20.00", "1000000", "0");

            given(userRepository.findById(2L)).willReturn(Optional.of(user));
            given(quoteRepository.findSubmittedByUserId(anyLong(), anyList())).willReturn(List.of(q));
            given(userStatsRepository.findByUserId(2L)).willReturn(Optional.empty());

            // when
            userStatsUpdateService.recalculate(2L);

            // then
            verify(userStatsRepository).save(any(UserStats.class));
        }

        @Test
        @DisplayName("기존 UserStats가 있으면 기존 객체를 update 후 저장한다")
        void success_updatesExistingStats() throws Exception {
            // given
            User user = buildUser(3L);
            Quote q = buildQuote(user, QuoteStatus.SENT, "1000000", "900000", "180000", "20.00", "1000000", "0");

            UserStats existing = UserStats.builder()
                    .id(10L).user(user).totalQuotes(5).approvedQuotes(2)
                    .totalAmount(new BigDecimal("3000000")).build();
            setField(existing, "updatedAt", LocalDateTime.now());

            given(userRepository.findById(3L)).willReturn(Optional.of(user));
            given(quoteRepository.findSubmittedByUserId(anyLong(), anyList())).willReturn(List.of(q));
            given(userStatsRepository.findByUserId(3L)).willReturn(Optional.of(existing));

            // when
            userStatsUpdateService.recalculate(3L);

            // then: 동일 객체를 저장
            verify(userStatsRepository).save(existing);
            assertThat(existing.getTotalQuotes()).isEqualTo(1);
            assertThat(existing.getSentQuotes()).isEqualTo(1);
        }

        @Test
        @DisplayName("제출된 견적이 없으면 모든 통계를 0으로 초기화한다")
        void success_noQuotes_allZero() throws Exception {
            // given
            User user = buildUser(4L);

            given(userRepository.findById(4L)).willReturn(Optional.of(user));
            given(quoteRepository.findSubmittedByUserId(anyLong(), anyList())).willReturn(List.of());
            given(userStatsRepository.findByUserId(4L)).willReturn(Optional.empty());

            // when
            userStatsUpdateService.recalculate(4L);

            // then
            ArgumentCaptor<UserStats> captor = ArgumentCaptor.forClass(UserStats.class);
            verify(userStatsRepository).save(captor.capture());
            UserStats saved = captor.getValue();

            assertThat(saved.getTotalQuotes()).isZero();
            assertThat(saved.getApprovedQuotes()).isZero();
            assertThat(saved.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(saved.getAverageDiscountRate()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(saved.getAverageProfitRate()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("할인율은 (할인금액/소계)*100 평균으로 계산된다")
        void success_discountRateCalculation() throws Exception {
            // given
            User user = buildUser(5L);
            // 할인율: 100000/1000000*100 = 10%, 50000/500000*100 = 10% → 평균 10%
            Quote q1 = buildQuote(user, QuoteStatus.SENT, "990000", "900000", "100000", "10.00", "1000000", "100000");
            Quote q2 = buildQuote(user, QuoteStatus.APPROVAL_NOT_REQUIRED, "495000", "450000", "50000", "10.00", "500000", "50000");

            given(userRepository.findById(5L)).willReturn(Optional.of(user));
            given(quoteRepository.findSubmittedByUserId(anyLong(), anyList())).willReturn(List.of(q1, q2));
            given(userStatsRepository.findByUserId(5L)).willReturn(Optional.empty());

            // when
            userStatsUpdateService.recalculate(5L);

            // then
            ArgumentCaptor<UserStats> captor = ArgumentCaptor.forClass(UserStats.class);
            verify(userStatsRepository).save(captor.capture());
            assertThat(captor.getValue().getAverageDiscountRate())
                    .isEqualByComparingTo(new BigDecimal("10.00"));
        }

        @Test
        @DisplayName("존재하지 않는 userId면 USER_NOT_FOUND 예외를 던진다")
        void fail_userNotFound() {
            // given
            given(userRepository.findById(99L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userStatsUpdateService.recalculate(99L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);

            verify(userStatsRepository, never()).save(any());
        }
    }

    // ── recalculateAllUsers ────────────────────────────────────────────────

    @Nested
    @DisplayName("recalculateAllUsers() 배치 스케줄러")
    class RecalculateAllUsers {

        @Test
        @DisplayName("대상 사용자가 여럿일 때 각각 recalculate가 실행된다")
        void success_multipleUsers() throws Exception {
            // given
            User u1 = buildUser(1L);
            User u2 = buildUser(2L);

            given(quoteRepository.findUserIdsWithSubmittedQuotes(anyList())).willReturn(List.of(1L, 2L));
            given(userRepository.findById(1L)).willReturn(Optional.of(u1));
            given(userRepository.findById(2L)).willReturn(Optional.of(u2));
            given(quoteRepository.findSubmittedByUserId(anyLong(), anyList())).willReturn(List.of());
            given(userStatsRepository.findByUserId(anyLong())).willReturn(Optional.empty());

            // when
            userStatsUpdateService.recalculateAllUsers();

            // then
            verify(userStatsRepository, times(2)).save(any(UserStats.class));
        }

        @Test
        @DisplayName("특정 사용자 갱신 실패 시 다른 사용자 갱신은 계속된다")
        void success_partialFailure_continuesOtherUsers() throws Exception {
            // given
            User u2 = buildUser(2L);

            given(quoteRepository.findUserIdsWithSubmittedQuotes(anyList())).willReturn(List.of(1L, 2L));
            given(userRepository.findById(1L)).willReturn(Optional.empty());   // userId=1 → 예외
            given(userRepository.findById(2L)).willReturn(Optional.of(u2));    // userId=2 → 정상
            given(quoteRepository.findSubmittedByUserId(eq(2L), anyList())).willReturn(List.of());
            given(userStatsRepository.findByUserId(2L)).willReturn(Optional.empty());

            // when: 예외가 외부로 전파되지 않아야 함
            userStatsUpdateService.recalculateAllUsers();

            // then: 정상 사용자만 저장됨
            verify(userStatsRepository, times(1)).save(any(UserStats.class));
        }

        @Test
        @DisplayName("대상 사용자가 없으면 save를 호출하지 않는다")
        void success_noTargetUsers() {
            // given
            given(quoteRepository.findUserIdsWithSubmittedQuotes(anyList())).willReturn(List.of());

            // when
            userStatsUpdateService.recalculateAllUsers();

            // then
            verify(userStatsRepository, never()).save(any());
        }
    }
}
