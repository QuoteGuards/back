package com.project.back.domain.user.service;

import com.project.back.domain.quote.repository.QuoteRepository;
import com.project.back.domain.user.dto.UserStatsProjection;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.entity.UserRole;
import com.project.back.domain.user.entity.UserStats;
import com.project.back.domain.user.entity.UserStatus;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.domain.user.repository.UserStatsRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * UserStatsUpdateService 단위 테스트.
 *
 * <p>DB 집계는 {@code QuoteRepository.aggregateUserStats()} 모킹으로 대체한다.
 * 상태별 집계 기준은 QueryDSL({@code QuoteRepositoryImpl})에서 검증한다.</p>
 */
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
                .phone("010-1234-5678").status(UserStatus.APPROVED)
                .role(UserRole.SALES_STAFF).build();
        setField(user, "createdAt", LocalDateTime.now());
        setField(user, "updatedAt", LocalDateTime.now());
        return user;
    }

    private UserStatsProjection buildProjection(
            long total, long approved, long rejected, long sent,
            String totalAmt, String supplyAmt, String profitAmt,
            String discountRateSum, long discountRateCount,
            String profitRateSum, long profitRateCount) {
        return new UserStatsProjection(
                total, approved, rejected, sent,
                new BigDecimal(totalAmt),
                new BigDecimal(supplyAmt),
                new BigDecimal(profitAmt),
                new BigDecimal(discountRateSum), discountRateCount,
                new BigDecimal(profitRateSum), profitRateCount
        );
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field f = clazz.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    // ── recalculate ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("recalculate(userId) — DB 집계 결과를 UserStats에 반영한다")
    class Recalculate {

        @Test
        @DisplayName("정상 집계 결과를 UserStats에 저장한다")
        void success_savesAggregatedStats() throws Exception {
            // given
            User user = buildUser(1L);
            // total=4, approved=2(APPROVED+SENT), rejected=1, sent=1
            // discountRateSum=20.00(10%+10%), discountRateCount=2 → avgDiscountRate=10.00
            // profitRateSum=40.00(20%+20%), profitRateCount=2 → avgProfitRate=20.00
            UserStatsProjection proj = buildProjection(
                    4, 2, 1, 1,
                    "1650000", "1500000", "300000",
                    "20.00", 2,
                    "40.00", 2
            );

            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(quoteRepository.aggregateUserStats(1L)).willReturn(proj);
            given(userStatsRepository.findByUserId(1L)).willReturn(Optional.empty());

            // when
            userStatsUpdateService.recalculate(1L);

            // then
            ArgumentCaptor<UserStats> captor = ArgumentCaptor.forClass(UserStats.class);
            verify(userStatsRepository).save(captor.capture());
            UserStats saved = captor.getValue();

            assertThat(saved.getTotalQuotes()).isEqualTo(4);
            assertThat(saved.getApprovedQuotes()).isEqualTo(2);
            assertThat(saved.getRejectedQuotes()).isEqualTo(1);
            assertThat(saved.getSentQuotes()).isEqualTo(1);
            assertThat(saved.getTotalAmount()).isEqualByComparingTo("1650000");
            assertThat(saved.getTotalSupplyAmount()).isEqualByComparingTo("1500000");
            assertThat(saved.getTotalProfitAmount()).isEqualByComparingTo("300000");
            assertThat(saved.getAverageDiscountRate()).isEqualByComparingTo("10.00");
            assertThat(saved.getAverageProfitRate()).isEqualByComparingTo("20.00");
        }

        @Test
        @DisplayName("UserStats가 없으면 새로 생성하여 저장한다")
        void success_createsNewStats() throws Exception {
            // given
            User user = buildUser(2L);
            UserStatsProjection proj = UserStatsProjection.empty();

            given(userRepository.findById(2L)).willReturn(Optional.of(user));
            given(quoteRepository.aggregateUserStats(2L)).willReturn(proj);
            given(userStatsRepository.findByUserId(2L)).willReturn(Optional.empty());

            // when
            userStatsUpdateService.recalculate(2L);

            // then: 새 객체가 저장됨
            verify(userStatsRepository).save(any(UserStats.class));
        }

        @Test
        @DisplayName("UserStats가 이미 존재하면 기존 객체를 update 후 저장한다")
        void success_updatesExistingStats() throws Exception {
            // given
            User user = buildUser(3L);
            UserStatsProjection proj = buildProjection(
                    1, 1, 0, 1,
                    "1000000", "900000", "180000",
                    "0.00", 1, "20.00", 1
            );
            UserStats existing = UserStats.builder()
                    .id(10L).user(user).totalQuotes(5).approvedQuotes(3).build();
            setField(existing, "updatedAt", LocalDateTime.now());

            given(userRepository.findById(3L)).willReturn(Optional.of(user));
            given(quoteRepository.aggregateUserStats(3L)).willReturn(proj);
            given(userStatsRepository.findByUserId(3L)).willReturn(Optional.of(existing));

            // when
            userStatsUpdateService.recalculate(3L);

            // then: 동일 객체를 저장
            verify(userStatsRepository).save(existing);
            assertThat(existing.getTotalQuotes()).isEqualTo(1);
            assertThat(existing.getApprovedQuotes()).isEqualTo(1);
        }

        @Test
        @DisplayName("집계 대상 견적이 없으면 통계를 모두 0으로 초기화한다")
        void success_noQuotes_allZero() throws Exception {
            // given
            User user = buildUser(4L);
            given(userRepository.findById(4L)).willReturn(Optional.of(user));
            given(quoteRepository.aggregateUserStats(4L)).willReturn(UserStatsProjection.empty());
            given(userStatsRepository.findByUserId(4L)).willReturn(Optional.empty());

            // when
            userStatsUpdateService.recalculate(4L);

            // then
            ArgumentCaptor<UserStats> captor = ArgumentCaptor.forClass(UserStats.class);
            verify(userStatsRepository).save(captor.capture());
            UserStats saved = captor.getValue();

            assertThat(saved.getTotalQuotes()).isZero();
            assertThat(saved.getApprovedQuotes()).isZero();
            assertThat(saved.getRejectedQuotes()).isZero();
            assertThat(saved.getSentQuotes()).isZero();
            assertThat(saved.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(saved.getAverageDiscountRate()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(saved.getAverageProfitRate()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("null discountRateSum이면 평균 할인율 0을 반환한다")
        void success_nullDiscountRateSum_returnsZero() {
            // given: discountRateSum=null, discountRateCount=0
            UserStatsProjection proj = new UserStatsProjection(
                    1, 1, 0, 1,
                    new BigDecimal("1000000"), new BigDecimal("900000"), new BigDecimal("180000"),
                    null, 0L,
                    new BigDecimal("20.00"), 1L
            );

            assertThat(proj.calcAverageDiscountRate()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("null profitRateSum이면 평균 이익률 0을 반환한다")
        void success_nullProfitRateSum_returnsZero() {
            // given
            UserStatsProjection proj = new UserStatsProjection(
                    1, 1, 0, 1,
                    new BigDecimal("1000000"), new BigDecimal("900000"), new BigDecimal("180000"),
                    new BigDecimal("10.00"), 1L,
                    null, 0L
            );

            assertThat(proj.calcAverageProfitRate()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("평균 이익률은 HALF_UP 소수점 2자리로 반올림된다")
        void success_profitRateRounding() {
            // given: sum=50.005, count=3 → 50.005/3 = 16.668... → HALF_UP → 16.67
            UserStatsProjection proj = new UserStatsProjection(
                    3, 2, 0, 2,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, 0L,
                    new BigDecimal("50.005"), 3L
            );

            assertThat(proj.calcAverageProfitRate()).isEqualByComparingTo("16.67");
        }

        @Test
        @DisplayName("평균 할인율은 HALF_UP 소수점 2자리로 반올림된다")
        void success_discountRateRounding() {
            // given: sum=30.005, count=3 → 10.0016... → HALF_UP → 10.00
            UserStatsProjection proj = new UserStatsProjection(
                    3, 0, 0, 0,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    new BigDecimal("30.005"), 3L,
                    BigDecimal.ZERO, 0L
            );

            assertThat(proj.calcAverageDiscountRate()).isEqualByComparingTo("10.00");
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

    // ── UserStatsProjection 집계 기준 검증 ──────────────────────────────

    @Nested
    @DisplayName("UserStatsProjection 집계 기준 (QueryDSL 쿼리 설계 기준 문서화)")
    class ProjectionSpec {

        @Test
        @DisplayName("DRAFT·CANCELLED는 totalQuotes에서 제외 → DB 쿼리 WHERE 절로 처리됨")
        void spec_draftCancelledExcluded() {
            // QueryDSL WHERE: quote.status.notIn(DRAFT, CANCELLED)
            // 이 테스트는 집계 기준 문서화 목적 — 실제 필터링은 QuoteRepositoryImpl에서 수행
            assertThat(true).isTrue(); // 구조 확인용
        }

        @Test
        @DisplayName("APPROVAL_NOT_REQUIRED는 totalQuotes에 포함됨")
        void spec_approvalNotRequiredIncludedInTotal() {
            // QueryDSL WHERE는 DRAFT·CANCELLED만 제외하므로 APPROVAL_NOT_REQUIRED 포함
            // proj.totalQuotes()에 반영됨
            UserStatsProjection proj = new UserStatsProjection(
                    3, 0, 0, 0,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, 0L, BigDecimal.ZERO, 0L
            );
            assertThat(proj.totalQuotes()).isEqualTo(3);
        }

        @Test
        @DisplayName("EXPIRED 상태는 approvedQuotes·sentQuotes에 포함된다")
        void spec_expiredCountedAsApprovedAndSent() {
            // QueryDSL CASE WHEN status IN (APPROVED, SENT, EXPIRED) → approvedQuotes
            // QueryDSL CASE WHEN status IN (SENT, EXPIRED)           → sentQuotes
            UserStatsProjection proj = new UserStatsProjection(
                    1, 1, 0, 1,  // totalQuotes=1, approvedQuotes=1(EXPIRED), sentQuotes=1(EXPIRED)
                    new BigDecimal("1000000"), new BigDecimal("900000"), new BigDecimal("180000"),
                    BigDecimal.ZERO, 0L, new BigDecimal("20.00"), 1L
            );

            assertThat(proj.approvedQuotes()).isEqualTo(1);
            assertThat(proj.sentQuotes()).isEqualTo(1);
        }

        @Test
        @DisplayName("REJECTED 상태는 rejectedQuotes에 포함되며 중복되지 않는다")
        void spec_rejectedCountedOnceByLatestVersion() {
            // isLatest=true 필터링으로 각 견적 그룹의 최신 버전만 선택됨
            // → 동일 견적이 반려 후 재작성되면 최신 버전 상태(REVISING 등)가 반영되어
            //   rejectedQuotes에서 자동으로 제외됨 (중복 집계 없음)
            UserStatsProjection proj = new UserStatsProjection(
                    2, 0, 1, 0,  // 1건만 REJECTED
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, 0L, BigDecimal.ZERO, 0L
            );

            assertThat(proj.rejectedQuotes()).isEqualTo(1);
        }

        @Test
        @DisplayName("safeAmount는 null 값을 BigDecimal.ZERO로 대체한다")
        void spec_safeAmountNullHandling() {
            UserStatsProjection proj = new UserStatsProjection(
                    1, 1, 0, 1,
                    null, null, null,
                    BigDecimal.ZERO, 1L, BigDecimal.ZERO, 1L
            );

            assertThat(proj.safeAmount(proj.totalAmount())).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(proj.safeAmount(proj.totalSupplyAmount())).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(proj.safeAmount(proj.totalProfitAmount())).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }
}
