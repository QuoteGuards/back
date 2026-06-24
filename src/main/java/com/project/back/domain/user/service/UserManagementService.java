package com.project.back.domain.user.service;

import com.project.back.domain.user.dto.request.AdminCreateUserRequest;
import com.project.back.domain.user.dto.request.ChangeUserRoleRequest;
import com.project.back.domain.user.dto.request.UpdateUserInfoRequest;
import com.project.back.domain.user.dto.response.AdminCreateUserResponse;
import com.project.back.domain.user.dto.response.UserDetailResponse;
import com.project.back.domain.user.dto.response.UserSummaryResponse;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.entity.UserRole;
import com.project.back.domain.user.entity.UserStatus;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${account.email-domain:quoteguard.com}")
    private String emailDomain;

    // 관리자가 신규 사원 계정을 직접 생성
    @Transactional
    public AdminCreateUserResponse createUser(AdminCreateUserRequest request) {
        // 1. 회원번호 자동 생성 (YYYY + 3자리 순번, 예: 2026001)
        String memberNumber = generateMemberNumber();

        // 2. 이메일 자동 생성 및 중복 검사 (안전망)
        String email = memberNumber + "@" + emailDomain;
        if (userRepository.existsByEmail(email)) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }

        // 3. 전화번호 중복 검사
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            if (userRepository.existsByPhone(request.getPhone())) {
                throw new CustomException(ErrorCode.DUPLICATE_PHONE);
            }
        }

        // 4. 임시 비밀번호 생성 (원문은 응답에 1회만 포함, 로그에 출력 금지)
        String temporaryPassword = generateTemporaryPassword();

        // 5. 계정 생성 - ACTIVE 상태, mustChangePassword=true
        User user = User.builder()
                .memberNumber(memberNumber)
                .email(email)
                .password(passwordEncoder.encode(temporaryPassword))
                .name(request.getName())
                .department(request.getDepartment())
                .position(request.getPosition())
                .phone(request.getPhone() != null && !request.getPhone().isBlank() ? request.getPhone() : null)
                .role(request.getRole())
                .status(UserStatus.ACTIVE)
                .mustChangePassword(true)
                .build();

        User savedUser = userRepository.save(user);

        return AdminCreateUserResponse.from(savedUser, temporaryPassword);
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

    // 사용자 비활성화 (ACTIVE → SUSPENDED)
    @Transactional
    public UserDetailResponse suspendUser(Long requesterId, Long userId) {
        if (requesterId.equals(userId)) {
            throw new CustomException(ErrorCode.CANNOT_MODIFY_SELF);
        }
        User user = findUserById(userId);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new CustomException(ErrorCode.USER_NOT_ACTIVE);
        }
        user.suspend(requesterId);
        return UserDetailResponse.from(user);
    }

    // 사용자 재활성화 (SUSPENDED → ACTIVE)
    @Transactional
    public UserDetailResponse reactivateUser(Long userId) {
        User user = findUserById(userId);
        if (user.getStatus() != UserStatus.SUSPENDED) {
            throw new CustomException(ErrorCode.USER_NOT_SUSPENDED);
        }
        user.reactivate();
        return UserDetailResponse.from(user);
    }

    /**
     * 회원번호 자동 생성: YYYY + 3자리 순번 (예: 2026001)
     * 동시성 이슈는 DB unique 제약 + @Transactional로 보호한다.
     */
    private String generateMemberNumber() {
        String yearPrefix = String.valueOf(LocalDate.now().getYear());
        int nextSeq = userRepository.findMaxMemberNumberByYearPrefix(yearPrefix)
                .map(max -> Integer.parseInt(max.substring(yearPrefix.length())) + 1)
                .orElse(1);
        return yearPrefix + String.format("%03d", nextSeq);
    }

    private String generateTemporaryPassword() {
        String raw = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "QG-" + raw;
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
