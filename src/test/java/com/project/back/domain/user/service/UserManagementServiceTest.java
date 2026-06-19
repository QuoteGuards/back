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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @InjectMocks
    private UserManagementService userManagementService;

    @Mock
    private UserRepository userRepository;

    // ── 공통 헬퍼 ──────────────────────────────────────────────────────────

    private User buildUser(Long id, UserStatus status, UserRole role) {
        try {
            User user = User.builder()
                    .id(id)
                    .email("user" + id + "@test.com")
                    .password("encoded")
                    .name("테스터" + id)
                    .department("영업1팀")
                    .position("대리")
                    .phone("010-1234-5678")
                    .status(status)
                    .role(role)
                    .build();
            setField(user, "createdAt", LocalDateTime.now());
            setField(user, "updatedAt", LocalDateTime.now());
            return user;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private <T> void setRequestField(T target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── 승인 대기 목록 조회 ────────────────────────────────────────────────

    @Nested
    @DisplayName("승인 대기 사용자 목록 조회")
    class GetPendingUsers {

        @Test
        @DisplayName("PENDING 사용자 목록 반환 성공")
        void getPendingUsers_success() {
            Pageable pageable = PageRequest.of(0, 20);
            User pending = buildUser(1L, UserStatus.PENDING, UserRole.SALES_STAFF);
            given(userRepository.findByStatus(UserStatus.PENDING, pageable))
                    .willReturn(new PageImpl<>(List.of(pending)));

            Page<UserSummaryResponse> result = userManagementService.getPendingUsers(pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().getFirst().getStatus()).isEqualTo("PENDING");
            assertThat(result.getContent().getFirst().getDepartment()).isEqualTo("영업1팀");
            assertThat(result.getContent().getFirst().getPosition()).isEqualTo("대리");
        }
    }

    // ── 전체 사용자 목록 조회 ──────────────────────────────────────────────

    @Nested
    @DisplayName("전체 사용자 목록 조회")
    class GetAllUsers {

        @Test
        @DisplayName("필터 없이 전체 조회 성공")
        void getAllUsers_noFilter_success() {
            Pageable pageable = PageRequest.of(0, 20);
            User user = buildUser(1L, UserStatus.APPROVED, UserRole.SALES_STAFF);
            given(userRepository.findAllWithFilters(null, null, null, pageable))
                    .willReturn(new PageImpl<>(List.of(user)));

            Page<UserSummaryResponse> result = userManagementService.getAllUsers(null, null, null, pageable);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("role + status + keyword 필터 적용 성공")
        void getAllUsers_withFilters_success() {
            Pageable pageable = PageRequest.of(0, 20);
            User user = buildUser(2L, UserStatus.APPROVED, UserRole.SALES_MANAGER);
            given(userRepository.findAllWithFilters(UserRole.SALES_MANAGER, UserStatus.APPROVED, "테스터", pageable))
                    .willReturn(new PageImpl<>(List.of(user)));

            Page<UserSummaryResponse> result = userManagementService.getAllUsers(
                    UserRole.SALES_MANAGER, UserStatus.APPROVED, "테스터", pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().getFirst().getRole()).isEqualTo("SALES_MANAGER");
        }
    }

    // ── 사용자 상세 조회 ───────────────────────────────────────────────────

    @Nested
    @DisplayName("사용자 상세 조회")
    class GetUserDetail {

        @Test
        @DisplayName("존재하는 사용자 상세 조회 성공 - department/position 포함")
        void getUserDetail_success() {
            User user = buildUser(1L, UserStatus.APPROVED, UserRole.SALES_STAFF);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            UserDetailResponse result = userManagementService.getUserDetail(1L);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getEmail()).isEqualTo("user1@test.com");
            assertThat(result.getDepartment()).isEqualTo("영업1팀");
            assertThat(result.getPosition()).isEqualTo("대리");
        }

        @Test
        @DisplayName("존재하지 않는 사용자 - USER_NOT_FOUND 예외")
        void getUserDetail_notFound() {
            given(userRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userManagementService.getUserDetail(99L))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_FOUND));
        }
    }

    // ── 가입 승인 ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("가입 승인")
    class ApproveUser {

        @Test
        @DisplayName("PENDING 사용자 승인 성공 - status APPROVED, approvedBy/approvedAt 기록")
        void approveUser_success() {
            User user = buildUser(1L, UserStatus.PENDING, UserRole.SALES_STAFF);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            UserDetailResponse result = userManagementService.approveUser(99L, 1L);

            assertThat(result.getStatus()).isEqualTo("APPROVED");
            assertThat(result.getApprovedBy()).isEqualTo(99L);
            assertThat(result.getApprovedAt()).isNotNull();
        }

        @Test
        @DisplayName("PENDING이 아닌 사용자 승인 - USER_NOT_PENDING 예외")
        void approveUser_notPending() {
            User user = buildUser(1L, UserStatus.APPROVED, UserRole.SALES_STAFF);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            assertThatThrownBy(() -> userManagementService.approveUser(99L, 1L))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_PENDING));
        }
    }

    // ── 가입 반려 ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("가입 반려")
    class RejectUser {

        @Test
        @DisplayName("PENDING 사용자 반려 성공 - status REJECTED, rejectReason/rejectedAt 기록")
        void rejectUser_success() {
            User user = buildUser(1L, UserStatus.PENDING, UserRole.SALES_STAFF);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            RejectUserRequest request = new RejectUserRequest();
            setRequestField(request, "rejectReason", "영업팀 인원 초과");

            UserDetailResponse result = userManagementService.rejectUser(1L, request);

            assertThat(result.getStatus()).isEqualTo("REJECTED");
            assertThat(result.getRejectReason()).isEqualTo("영업팀 인원 초과");
            assertThat(result.getRejectedAt()).isNotNull();
        }

        @Test
        @DisplayName("PENDING이 아닌 사용자 반려 - USER_NOT_PENDING 예외")
        void rejectUser_notPending() {
            User user = buildUser(1L, UserStatus.REJECTED, UserRole.SALES_STAFF);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            RejectUserRequest request = new RejectUserRequest();
            setRequestField(request, "rejectReason", "사유");

            assertThatThrownBy(() -> userManagementService.rejectUser(1L, request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_PENDING));
        }
    }

    // ── 사용자 정보 수정 ───────────────────────────────────────────────────

    @Nested
    @DisplayName("사용자 정보 수정")
    class UpdateUserInfo {

        @Test
        @DisplayName("이름/부서/직급/전화번호 수정 성공")
        void updateUserInfo_success() {
            User user = buildUser(1L, UserStatus.APPROVED, UserRole.SALES_STAFF);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            UpdateUserInfoRequest request = new UpdateUserInfoRequest();
            setRequestField(request, "name", "새이름");
            setRequestField(request, "department", "영업2팀");
            setRequestField(request, "position", "과장");
            setRequestField(request, "phone", "010-9999-8888");

            UserDetailResponse result = userManagementService.updateUserInfo(1L, request);

            assertThat(result.getName()).isEqualTo("새이름");
            assertThat(result.getDepartment()).isEqualTo("영업2팀");
            assertThat(result.getPosition()).isEqualTo("과장");
            assertThat(result.getPhone()).isEqualTo("010-9999-8888");
        }

        @Test
        @DisplayName("존재하지 않는 사용자 수정 - USER_NOT_FOUND 예외")
        void updateUserInfo_notFound() {
            given(userRepository.findById(99L)).willReturn(Optional.empty());
            UpdateUserInfoRequest request = new UpdateUserInfoRequest();

            assertThatThrownBy(() -> userManagementService.updateUserInfo(99L, request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_FOUND));
        }
    }

    // ── 사용자 권한 변경 ───────────────────────────────────────────────────

    @Nested
    @DisplayName("사용자 권한 변경")
    class ChangeUserRole {

        @Test
        @DisplayName("권한 변경 성공")
        void changeRole_success() {
            User user = buildUser(2L, UserStatus.APPROVED, UserRole.SALES_STAFF);
            given(userRepository.findById(2L)).willReturn(Optional.of(user));

            ChangeUserRoleRequest request = new ChangeUserRoleRequest();
            setRequestField(request, "role", UserRole.SALES_MANAGER);

            UserDetailResponse result = userManagementService.changeUserRole(1L, 2L, request);

            assertThat(result.getRole()).isEqualTo("SALES_MANAGER");
        }

        @Test
        @DisplayName("자기 자신 권한 변경 - CANNOT_MODIFY_SELF 예외")
        void changeRole_self() {
            ChangeUserRoleRequest request = new ChangeUserRoleRequest();
            setRequestField(request, "role", UserRole.SALES_MANAGER);

            assertThatThrownBy(() -> userManagementService.changeUserRole(1L, 1L, request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.CANNOT_MODIFY_SELF));
        }
    }

    // ── 사용자 비활성화 ────────────────────────────────────────────────────

    @Nested
    @DisplayName("사용자 비활성화")
    class SuspendUser {

        @Test
        @DisplayName("APPROVED 사용자 비활성화 성공 - status SUSPENDED, suspendedBy/suspendedAt 기록")
        void suspendUser_success() {
            User user = buildUser(2L, UserStatus.APPROVED, UserRole.SALES_STAFF);
            given(userRepository.findById(2L)).willReturn(Optional.of(user));

            UserDetailResponse result = userManagementService.suspendUser(1L, 2L);

            assertThat(result.getStatus()).isEqualTo("SUSPENDED");
            assertThat(result.getSuspendedBy()).isEqualTo(1L);
            assertThat(result.getSuspendedAt()).isNotNull();
        }

        @Test
        @DisplayName("자기 자신 비활성화 - CANNOT_MODIFY_SELF 예외")
        void suspendUser_self() {
            assertThatThrownBy(() -> userManagementService.suspendUser(1L, 1L))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.CANNOT_MODIFY_SELF));
        }

        @Test
        @DisplayName("APPROVED가 아닌 사용자 비활성화 - USER_NOT_APPROVED 예외")
        void suspendUser_notApproved() {
            User user = buildUser(2L, UserStatus.PENDING, UserRole.SALES_STAFF);
            given(userRepository.findById(2L)).willReturn(Optional.of(user));

            assertThatThrownBy(() -> userManagementService.suspendUser(1L, 2L))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_APPROVED));
        }
    }

    // ── 사용자 재활성화 ────────────────────────────────────────────────────

    @Nested
    @DisplayName("사용자 재활성화")
    class ReactivateUser {

        @Test
        @DisplayName("SUSPENDED 사용자 재활성화 성공 - status APPROVED, suspendedBy/suspendedAt 초기화")
        void reactivateUser_success() {
            User user = buildUser(1L, UserStatus.SUSPENDED, UserRole.SALES_STAFF);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            UserDetailResponse result = userManagementService.reactivateUser(1L);

            assertThat(result.getStatus()).isEqualTo("APPROVED");
            assertThat(result.getSuspendedBy()).isNull();
            assertThat(result.getSuspendedAt()).isNull();
        }

        @Test
        @DisplayName("SUSPENDED가 아닌 사용자 재활성화 - USER_NOT_SUSPENDED 예외")
        void reactivateUser_notSuspended() {
            User user = buildUser(1L, UserStatus.APPROVED, UserRole.SALES_STAFF);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            assertThatThrownBy(() -> userManagementService.reactivateUser(1L))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_SUSPENDED));
        }
    }
}
