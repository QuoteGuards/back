package com.project.back.domain.user.service;

import com.project.back.domain.quote.repository.QuoteRepository;
import com.project.back.domain.user.repository.UserStatsRepository;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserStatsBatchServiceTest {

    @InjectMocks
    private UserStatsBatchService userStatsBatchService;

    @Mock
    private UserStatsUpdateService userStatsUpdateService;

    @Mock
    private UserStatsRepository userStatsRepository;

    @Mock
    private QuoteRepository quoteRepository;

    @Test
    @DisplayName("배치 실행 시 UserStats 보유 사용자와 견적 생성 사용자를 모두 처리한다")
    void recalculateAllUsers_processesUnionOfBothSources() {
        // given: UserStats 보유 userId=1, 견적 생성 userId=2 (합집합 = {1, 2})
        given(userStatsRepository.findAllUserIds()).willReturn(List.of(1L));
        given(quoteRepository.findAllCreatedByUserIds()).willReturn(List.of(2L));

        // when
        userStatsBatchService.recalculateAllUsers();

        // then
        verify(userStatsUpdateService).recalculate(1L);
        verify(userStatsUpdateService).recalculate(2L);
    }

    @Test
    @DisplayName("두 소스에 동일 userId가 있으면 한 번만 처리한다")
    void recalculateAllUsers_deduplicatesUserIds() {
        // given: 같은 userId=1이 두 소스 모두에 존재
        given(userStatsRepository.findAllUserIds()).willReturn(List.of(1L));
        given(quoteRepository.findAllCreatedByUserIds()).willReturn(List.of(1L));

        // when
        userStatsBatchService.recalculateAllUsers();

        // then: 1회만 호출
        verify(userStatsUpdateService, times(1)).recalculate(1L);
    }

    @Test
    @DisplayName("한 사용자 처리 실패 시 예외를 삼키고 나머지 사용자를 계속 처리한다")
    void recalculateAllUsers_partialFailureIsIsolated() {
        // given: userId=1 실패, userId=2 성공
        given(userStatsRepository.findAllUserIds()).willReturn(List.of(1L, 2L));
        given(quoteRepository.findAllCreatedByUserIds()).willReturn(List.of());

        willThrow(new CustomException(ErrorCode.USER_NOT_FOUND))
                .given(userStatsUpdateService).recalculate(1L);

        // when: 예외가 전파되지 않아야 함
        userStatsBatchService.recalculateAllUsers();

        // then: 실패한 1L 이후에도 2L 처리 완료
        verify(userStatsUpdateService).recalculate(1L);
        verify(userStatsUpdateService).recalculate(2L);
    }

    @Test
    @DisplayName("대상 사용자가 없으면 recalculate를 호출하지 않는다")
    void recalculateAllUsers_noTargetUsers_doesNothing() {
        // given
        given(userStatsRepository.findAllUserIds()).willReturn(List.of());
        given(quoteRepository.findAllCreatedByUserIds()).willReturn(List.of());

        // when
        userStatsBatchService.recalculateAllUsers();

        // then
        verifyNoInteractions(userStatsUpdateService);
    }
}
