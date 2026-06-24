package com.project.back.domain.user.service;

import com.project.back.domain.user.dto.response.UserStatsResponse;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserStatsServiceTest {

    @InjectMocks
    private UserStatsService userStatsService;

    @Mock
    private UserStatsRepository userStatsRepository;

    @Mock
    private UserRepository userRepository;

    // ── 공통 헬퍼 ──────────────────────────────────────────────────────────

    private User buildUser(Long id) {
        try {
            User user = User.builder()
                    .id(id)
                    .email("user" + id + "@test.com")
                    .password("encoded")
                    .name("테스터" + id)
                    .department("영업1팀")
                    .position("대리")
                    .phone("010-1234-5678")
                    .status(UserStatus.ACTIVE)
                    .role(UserRole.SALES_STAFF)
                    .build();
            setField(user, "createdAt", LocalDateTime.now());
            setField(user, "updatedAt", LocalDateTime.now());
            return user;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private UserStats buildStats(User user) {
        try {
            UserStats stats = UserStats.builder()
                    .id(1L)
                    .user(user)
                    .totalQuotes(10)
                    .approvedQuotes(5)
                    .rejectedQuotes(2)
                    .sentQuotes(3)
                    .totalAmount(new BigDecimal("5000000.00"))
                    .totalSupplyAmount(new BigDecimal("4000000.00"))
                    .totalProfitAmount(new BigDecimal("1000000.00"))
                    .averageDiscountRate(new BigDecimal("5.50"))
                    .averageProfitRate(new BigDecimal("20.00"))
                    .build();
            setField(stats, "updatedAt", LocalDateTime.now());
            return stats;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    // ── getMyStats 테스트 ───────────────────────────────────────────────

    @Nested
    @DisplayName("내 통계 조회 (getMyStats)")
    class GetMyStats {

        @Test
        @DisplayName("통계 데이터가 있으면 정상 반환한다")
        void success_withStats() {
            // given
            User user = buildUser(1L);
            UserStats stats = buildStats(user);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(userStatsRepository.findByUserId(1L)).willReturn(Optional.of(stats));

            // when
            UserStatsResponse response = userStatsService.getMyStats(1L);

            // then
            assertThat(response.getUserId()).isEqualTo(1L);
            assertThat(response.getUserName()).isEqualTo("테스터1");
            assertThat(response.getTotalQuotes()).isEqualTo(10);
            assertThat(response.getApprovedQuotes()).isEqualTo(5);
            assertThat(response.getRejectedQuotes()).isEqualTo(2);
            assertThat(response.getSentQuotes()).isEqualTo(3);
            assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("5000000.00"));
            assertThat(response.getAverageDiscountRate()).isEqualByComparingTo(new BigDecimal("5.50"));
            assertThat(response.getAverageProfitRate()).isEqualByComparingTo(new BigDecimal("20.00"));
        }

        @Test
        @DisplayName("통계 데이터가 없으면 0값으로 채운 응답을 반환한다")
        void success_emptyStats_returnsZero() {
            // given
            User user = buildUser(2L);
            given(userRepository.findById(2L)).willReturn(Optional.of(user));
            given(userStatsRepository.findByUserId(2L)).willReturn(Optional.empty());

            // when
            UserStatsResponse response = userStatsService.getMyStats(2L);

            // then
            assertThat(response.getUserId()).isEqualTo(2L);
            assertThat(response.getUserName()).isEqualTo("테스터2");
            assertThat(response.getTotalQuotes()).isZero();
            assertThat(response.getApprovedQuotes()).isZero();
            assertThat(response.getRejectedQuotes()).isZero();
            assertThat(response.getSentQuotes()).isZero();
            assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.getTotalSupplyAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.getTotalProfitAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.getAverageDiscountRate()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.getAverageProfitRate()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("존재하지 않는 userId면 USER_NOT_FOUND 예외를 던진다")
        void fail_userNotFound() {
            // given
            given(userRepository.findById(99L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userStatsService.getMyStats(99L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    // ── getUserStats 테스트 ─────────────────────────────────────────────

    @Nested
    @DisplayName("관리자 사용자별 통계 조회 (getUserStats)")
    class GetUserStats {

        @Test
        @DisplayName("통계 데이터가 있으면 정상 반환한다")
        void success_withStats() {
            // given
            User user = buildUser(3L);
            UserStats stats = buildStats(user);
            given(userRepository.findById(3L)).willReturn(Optional.of(user));
            given(userStatsRepository.findByUserId(3L)).willReturn(Optional.of(stats));

            // when
            UserStatsResponse response = userStatsService.getUserStats(3L);

            // then
            assertThat(response.getUserId()).isEqualTo(3L);
            assertThat(response.getTotalQuotes()).isEqualTo(10);
            assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("5000000.00"));
        }

        @Test
        @DisplayName("통계 데이터가 없으면 0값으로 채운 응답을 반환한다")
        void success_emptyStats_returnsZero() {
            // given
            User user = buildUser(4L);
            given(userRepository.findById(4L)).willReturn(Optional.of(user));
            given(userStatsRepository.findByUserId(4L)).willReturn(Optional.empty());

            // when
            UserStatsResponse response = userStatsService.getUserStats(4L);

            // then
            assertThat(response.getUserId()).isEqualTo(4L);
            assertThat(response.getTotalQuotes()).isZero();
            assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.getTotalProfitAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("존재하지 않는 userId면 USER_NOT_FOUND 예외를 던진다")
        void fail_userNotFound() {
            // given
            given(userRepository.findById(99L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userStatsService.getUserStats(99L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }
}
