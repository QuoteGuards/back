package com.project.back.domain.user.service;

import com.project.back.domain.auth.service.InitialPasswordSetupService;
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
    private final InitialPasswordSetupService initialPasswordSetupService;

    @Value("${account.email-domain:quoteguard.com}")
    private String emailDomain;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 관리자가 신규 사원 계정을 생성한다.
     *
     * <p>임시 비밀번호를 발급하지 않는다. 대신:
     * <ul>
     *   <li>비밀번호 자리에는 랜덤 BCrypt 해시(사용 불가) 저장</li>
     *   <li>passwordInitialized=false 상태로 생성 → 로그인 불가</li>
     *   <li>등록된 이메일로 초기 비밀번호 설정 링크 발송</li>
     * </ul>
     */
    @Transactional
    public AdminCreateUserResponse createUser(AdminCreateUserRequest request, Long createdBy) {
        // 1. 회원번호 자동 생성
        String memberNumber = generateMemberNumber();

        // 2. 이메일 자동 생성 및 중복 검사
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

        // 5. 사용 불가 비밀번호 placeholder 생성 (원문 미사용 — BCrypt만 저장)
        String unusablePlaceholder = generateUnusablePlaceholder();

        // 6. 계정 생성 - passwordInitialized=false (이메일 초기 설정 링크로 로그인 가능해짐)
        User user = User.builder()
                .memberNumber(memberNumber)
                .email(email)
                .password(passwordEncoder.encode(unusablePlaceholder))
                .name(request.getName())
                .department(request.getDepartment())
                .position(request.getPosition())
                .phone(request.getPhone() != null && !request.getPhone().isBlank() ? request.getPhone() : null)
                .role(request.getRole())
                .status(UserStatus.ACTIVE)
                .passwordInitialized(false)
                .createdBy(createdBy)
                .build();

        User savedUser = userRepository.save(user);

        // 7. 초기 비밀번호 설정 이메일 발송 (AFTER_COMMIT 이벤트로 위임)
        initialPasswordSetupService.sendSetupLink(savedUser);

        return AdminCreateUserResponse.from(savedUser);
    }

    // 전체 사용자 목록 조회
    @Transactional(readOnly = true)
    public Page<UserSummaryResponse> getAllUsers(UserRole role, UserStatus status, String keyword, Pageable pageable) {
        return userRepository.findAllWithFilters(role, status, keyword, pageable)
                .map(UserSummaryResponse::from);
    }

    // 사용자 상세 조회
    @Transactional(readOnly = true)
    public UserDetailResponse getUserDetail(Long userId) {
        User user = findUserById(userId);
        return buildUserDetailResponse(user);
    }

    // 사용자 정보 수정
    @Transactional
    public UserDetailResponse updateUserInfo(Long userId, UpdateUserInfoRequest request) {
        User user = findUserById(userId);
        user.updateInfo(request.getName(), request.getPhone(), request.getDepartment(), request.getPosition());
        User saved = userRepository.saveAndFlush(user);
        return buildUserDetailResponse(saved);
    }

    // 사용자 권한 변경
    @Transactional
    public UserDetailResponse changeUserRole(Long requesterId, Long userId, ChangeUserRoleRequest request) {
        if (requesterId.equals(userId)) {
            throw new CustomException(ErrorCode.CANNOT_MODIFY_SELF);
        }
        User user = findUserById(userId);
        user.changeRole(request.getRole());
        User saved = userRepository.saveAndFlush(user);
        return buildUserDetailResponse(saved);
    }

    // 사용자 비활성화
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
        User saved = userRepository.saveAndFlush(user);
        return buildUserDetailResponse(saved);
    }

    // 사용자 재활성화
    @Transactional
    public UserDetailResponse reactivateUser(Long userId) {
        User user = findUserById(userId);
        if (user.getStatus() != UserStatus.SUSPENDED) {
            throw new CustomException(ErrorCode.USER_NOT_SUSPENDED);
        }
        user.reactivate();
        User saved = userRepository.saveAndFlush(user);
        return buildUserDetailResponse(saved);
    }

    /**
     * 계정 생성자 메타데이터(createdByName, createdByMemberNumber)를 보강하여
     * UserDetailResponse를 조립한다. UserDetailResponse를 반환하는 모든 경로에서
     * 동일한 보강 로직을 사용하도록 공통 헬퍼로 분리했다.
     */
    private UserDetailResponse buildUserDetailResponse(User user) {
        String createdByName = null;
        String createdByMemberNumber = null;
        if (user.getCreatedBy() != null) {
            var creator = userRepository.findById(user.getCreatedBy());
            createdByName = creator.map(User::getName).orElse(null);
            createdByMemberNumber = creator.map(User::getMemberNumber).orElse(null);
        }
        return UserDetailResponse.from(user, createdByName, createdByMemberNumber);
    }

    // 사용자 삭제 (소프트 삭제)
    @Transactional
    public void deleteUser(Long requesterId, Long userId) {
        if (requesterId.equals(userId)) {
            throw new CustomException(ErrorCode.CANNOT_MODIFY_SELF);
        }
        User user = findUserById(userId);
        if (user.getStatus() == UserStatus.DELETED) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        user.delete();
    }

    /**
     * 회원번호 자동 생성: YY(년도 뒤 2자리) + 5자리 난수 (예: 2684921)
     * 중복 시 최대 5회 재시도한다.
     */
    private String generateMemberNumber() {
        String yearPrefix = String.format("%02d", LocalDate.now().getYear() % 100);
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = yearPrefix + String.format("%05d", SECURE_RANDOM.nextInt(100_000));
            if (!userRepository.existsByMemberNumber(candidate)) {
                return candidate;
            }
        }
        throw new CustomException(ErrorCode.MEMBER_NUMBER_GENERATION_FAILED);
    }

    /**
     * 사용 불가 비밀번호 placeholder를 생성한다.
     * 외부에 절대 노출하지 않으며 로그인에 사용할 수 없다.
     * BCrypt 해시 후 저장하므로 원문 복원은 불가능하다.
     */
    private String generateUnusablePlaceholder() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return java.util.HexFormat.of().formatHex(bytes);
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
