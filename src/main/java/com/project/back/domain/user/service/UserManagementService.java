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

import java.security.SecureRandom;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${account.email-domain:quoteguard.com}")
    private String emailDomain;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

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

        // 3. SUPER_ADMIN 역할 생성 차단
        if (request.getRole() == UserRole.SUPER_ADMIN) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        // 4. 전화번호 중복 검사
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            if (userRepository.existsByPhone(request.getPhone())) {
                throw new CustomException(ErrorCode.DUPLICATE_PHONE);
            }
        }

        // 5. 임시 비밀번호 생성 (원문은 응답에 1회만 포함, 로그에 출력 금지)
        String temporaryPassword = generateTemporaryPassword();

        // 6. 계정 생성 - ACTIVE 상태, mustChangePassword=true
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
     * 회원번호 자동 생성: YY(년도 뒤 2자리) + 5자리 난수 (예: 2684921)
     * 중복 시 최대 5회 재시도한다.
     */
    private String generateMemberNumber() {
        String yearPrefix = String.valueOf(LocalDate.now().getYear() % 100);
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = yearPrefix + String.format("%05d", SECURE_RANDOM.nextInt(100_000));
            if (!userRepository.existsByMemberNumber(candidate)) {
                return candidate;
            }
        }
        throw new CustomException(ErrorCode.MEMBER_NUMBER_GENERATION_FAILED);
    }

    private String generateTemporaryPassword() {
        StringBuilder raw = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            raw.append(PASSWORD_CHARS.charAt(SECURE_RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return "QG-" + raw;
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
