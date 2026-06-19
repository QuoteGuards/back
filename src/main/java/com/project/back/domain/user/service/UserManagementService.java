package com.project.back.domain.user.service;

import com.project.back.domain.user.dto.request.ChangeUserRoleRequest;
import com.project.back.domain.user.dto.request.RejectUserRequest;
import com.project.back.domain.user.dto.request.UpdateUserInfoRequest;
import com.project.back.domain.user.dto.response.UserDetailResponse;
import com.project.back.domain.user.dto.response.UserSummaryResponse;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.entity.UserRole;
import com.project.back.domain.user.entity.UserStatus;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository userRepository;

    // 승인 대기 사용자 목록 조회
    @Transactional(readOnly = true)
    public Page<UserSummaryResponse> getPendingUsers(Pageable pageable) {
        return userRepository.findByStatus(UserStatus.PENDING, pageable)
                .map(UserSummaryResponse::from);
    }

    // 전체 사용자 목록 조회 (role, status, keyword 필터 + 페이징)
    @Transactional(readOnly = true)
    public Page<UserSummaryResponse> getAllUsers(UserRole role, UserStatus status, String keyword, Pageable pageable) {
        return userRepository.findAllWithFilters(role, status, keyword, pageable)
                .map(UserSummaryResponse::from);
    }

    // 사용자 상세 조회
    @Transactional(readOnly = true)
    public UserDetailResponse getUserDetail(Long userId) {
        return UserDetailResponse.from(findUserById(userId));
    }

    // 가입 승인 (PENDING → APPROVED) - approverId: 승인한 관리자 ID
    @Transactional
    public UserDetailResponse approveUser(Long approverId, Long userId) {
        User user = findUserById(userId);
        if (user.getStatus() != UserStatus.PENDING) {
            throw new CustomException(ErrorCode.USER_NOT_PENDING);
        }
        user.approve(approverId);
        return UserDetailResponse.from(user);
    }

    // 가입 반려 (PENDING → REJECTED) - rejectReason: 반려 사유
    @Transactional
    public UserDetailResponse rejectUser(Long userId, RejectUserRequest request) {
        User user = findUserById(userId);
        if (user.getStatus() != UserStatus.PENDING) {
            throw new CustomException(ErrorCode.USER_NOT_PENDING);
        }
        user.reject(request.getRejectReason());
        return UserDetailResponse.from(user);
    }

    // 사용자 정보 수정 (이름, 부서, 직급, 전화번호)
    @Transactional
    public UserDetailResponse updateUserInfo(Long userId, UpdateUserInfoRequest request) {
        User user = findUserById(userId);
        user.updateInfo(request.getName(), request.getPhone(), request.getDepartment(), request.getPosition());
        return UserDetailResponse.from(user);
    }

    // 사용자 권한 변경 (자기 자신 변경 불가)
    @Transactional
    public UserDetailResponse changeUserRole(Long requesterId, Long userId, ChangeUserRoleRequest request) {
        if (requesterId.equals(userId)) {
            throw new CustomException(ErrorCode.CANNOT_MODIFY_SELF);
        }
        User user = findUserById(userId);
        user.changeRole(request.getRole());
        return UserDetailResponse.from(user);
    }

    // 사용자 비활성화 (APPROVED → SUSPENDED) - 자기 자신 비활성화 불가
    @Transactional
    public UserDetailResponse suspendUser(Long requesterId, Long userId) {
        if (requesterId.equals(userId)) {
            throw new CustomException(ErrorCode.CANNOT_MODIFY_SELF);
        }
        User user = findUserById(userId);
        if (user.getStatus() != UserStatus.APPROVED) {
            throw new CustomException(ErrorCode.USER_NOT_APPROVED);
        }
        user.suspend(requesterId);
        return UserDetailResponse.from(user);
    }

    // 사용자 재활성화 (SUSPENDED → APPROVED)
    @Transactional
    public UserDetailResponse reactivateUser(Long userId) {
        User user = findUserById(userId);
        if (user.getStatus() != UserStatus.SUSPENDED) {
            throw new CustomException(ErrorCode.USER_NOT_SUSPENDED);
        }
        user.reactivate();
        return UserDetailResponse.from(user);
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
