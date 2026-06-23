package com.project.back.domain.user.service;

import com.project.back.domain.user.dto.response.UserStatsResponse;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.entity.UserStats;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.domain.user.repository.UserStatsRepository;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserStatsService {

    private final UserStatsRepository userStatsRepository;
    private final UserRepository userRepository;

    /**
     * 내 통계 조회 (SALES_STAFF: 본인 통계만 조회 가능)
     * 통계 데이터가 없으면 0값으로 채운 응답 반환
     */
    @Transactional(readOnly = true)
    public UserStatsResponse getMyStats(Long userId) {
        User user = findUserById(userId);
        return userStatsRepository.findByUserId(userId)
                .map(UserStatsResponse::from)
                .orElse(UserStatsResponse.empty(user.getId(), user.getName()));
    }

    /**
     * 관리자용 사용자별 통계 조회 (SUPER_ADMIN, SALES_MANAGER 사용 가능)
     * 통계 데이터가 없으면 0값으로 채운 응답 반환
     */
    @Transactional(readOnly = true)
    public UserStatsResponse getUserStats(Long userId) {
        User user = findUserById(userId);
        return userStatsRepository.findByUserId(userId)
                .map(UserStatsResponse::from)
                .orElse(UserStatsResponse.empty(user.getId(), user.getName()));
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
