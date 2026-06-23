package com.project.back.domain.user.service;

import com.project.back.domain.user.dto.request.ChangeMyPasswordRequest;
import com.project.back.domain.user.dto.request.UpdateMyProfileRequest;
import com.project.back.domain.user.dto.response.MyProfileResponse;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MyProfileServiceTest {

    @InjectMocks
    private MyProfileService myProfileService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    // ── 공통 헬퍼 ─────────────────────────────────────────────────────────

    private User buildApprovedUser(Long id) {
        try {
            User user = User.builder()
                    .id(id)
                    .email("user@test.com")
                    .password("encodedOldPassword")
                    .name("홍길동")
                    .department("영업1팀")
                    .position("대리")
                    .phone("010-1234-5678")
                    .status(UserStatus.APPROVED)
                    .role(UserRole.SALES_STAFF)
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

    // ── 프로필 수정 ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("내 프로필 수정")
    class UpdateMyProfile {

        @Test
        @DisplayName("이름과 전화번호 모두 수정 성공")
        void updateMyProfile_nameAndPhone_success() {
            User user = buildApprovedUser(1L);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(userRepository.existsByPhoneAndIdNot("010-9999-0000", 1L)).willReturn(false);

            UpdateMyProfileRequest request = new UpdateMyProfileRequest();
            setRequestField(request, "name", "김철수");
            setRequestField(request, "phone", "010-9999-0000");

            MyProfileResponse result = myProfileService.updateMyProfile(1L, request);

            assertThat(result.getName()).isEqualTo("김철수");
            assertThat(result.getPhone()).isEqualTo("010-9999-0000");
        }

        @Test
        @DisplayName("이름만 수정 성공 - 전화번호 변경 없음")
        void updateMyProfile_nameOnly_success() {
            User user = buildApprovedUser(1L);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            UpdateMyProfileRequest request = new UpdateMyProfileRequest();
            setRequestField(request, "name", "이영희");

            MyProfileResponse result = myProfileService.updateMyProfile(1L, request);

            assertThat(result.getName()).isEqualTo("이영희");
            assertThat(result.getPhone()).isEqualTo("010-1234-5678"); // 기존 전화번호 유지
        }

        @Test
        @DisplayName("전화번호만 수정 성공 - 이름 변경 없음")
        void updateMyProfile_phoneOnly_success() {
            User user = buildApprovedUser(1L);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(userRepository.existsByPhoneAndIdNot("010-5555-6666", 1L)).willReturn(false);

            UpdateMyProfileRequest request = new UpdateMyProfileRequest();
            setRequestField(request, "phone", "010-5555-6666");

            MyProfileResponse result = myProfileService.updateMyProfile(1L, request);

            assertThat(result.getName()).isEqualTo("홍길동"); // 기존 이름 유지
            assertThat(result.getPhone()).isEqualTo("010-5555-6666");
        }

        @Test
        @DisplayName("중복 전화번호로 변경 시 DUPLICATE_PHONE 예외")
        void updateMyProfile_duplicatePhone_throwsException() {
            User user = buildApprovedUser(1L);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(userRepository.existsByPhoneAndIdNot("010-7777-8888", 1L)).willReturn(true);

            UpdateMyProfileRequest request = new UpdateMyProfileRequest();
            setRequestField(request, "phone", "010-7777-8888");

            assertThatThrownBy(() -> myProfileService.updateMyProfile(1L, request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.DUPLICATE_PHONE));
        }

        @Test
        @DisplayName("존재하지 않는 사용자 수정 시 USER_NOT_FOUND 예외")
        void updateMyProfile_userNotFound_throwsException() {
            given(userRepository.findById(99L)).willReturn(Optional.empty());
            UpdateMyProfileRequest request = new UpdateMyProfileRequest();

            assertThatThrownBy(() -> myProfileService.updateMyProfile(99L, request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_FOUND));
        }

        @Test
        @DisplayName("수정 결과에 role, status, department, position이 변경되지 않음")
        void updateMyProfile_restrictedFieldsNotChanged() {
            User user = buildApprovedUser(1L);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            UpdateMyProfileRequest request = new UpdateMyProfileRequest();
            setRequestField(request, "name", "새이름");

            MyProfileResponse result = myProfileService.updateMyProfile(1L, request);

            assertThat(result.getRole()).isEqualTo("SALES_STAFF");
            assertThat(result.getStatus()).isEqualTo("APPROVED");
            assertThat(result.getDepartment()).isEqualTo("영업1팀");
            assertThat(result.getPosition()).isEqualTo("대리");
            assertThat(result.getEmail()).isEqualTo("user@test.com");
        }
    }

    // ── 비밀번호 변경 ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("비밀번호 변경")
    class ChangeMyPassword {

        @Test
        @DisplayName("비밀번호 변경 성공 - 암호화된 비밀번호로 저장")
        void changeMyPassword_success() {
            User user = buildApprovedUser(1L);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(passwordEncoder.matches("OldPass@1", "encodedOldPassword")).willReturn(true);
            given(passwordEncoder.matches("NewPass@1", "encodedOldPassword")).willReturn(false);
            given(passwordEncoder.encode("NewPass@1")).willReturn("encodedNewPassword");

            ChangeMyPasswordRequest request = new ChangeMyPasswordRequest();
            setRequestField(request, "currentPassword", "OldPass@1");
            setRequestField(request, "newPassword", "NewPass@1");
            setRequestField(request, "newPasswordConfirm", "NewPass@1");

            myProfileService.changeMyPassword(1L, request);

            verify(passwordEncoder).encode("NewPass@1");
            assertThat(user.getPassword()).isEqualTo("encodedNewPassword");
        }

        @Test
        @DisplayName("현재 비밀번호 불일치 - INVALID_PASSWORD 예외")
        void changeMyPassword_wrongCurrentPassword_throwsException() {
            User user = buildApprovedUser(1L);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(passwordEncoder.matches("WrongPass@1", "encodedOldPassword")).willReturn(false);

            ChangeMyPasswordRequest request = new ChangeMyPasswordRequest();
            setRequestField(request, "currentPassword", "WrongPass@1");
            setRequestField(request, "newPassword", "NewPass@1");
            setRequestField(request, "newPasswordConfirm", "NewPass@1");

            assertThatThrownBy(() -> myProfileService.changeMyPassword(1L, request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_PASSWORD));
        }

        @Test
        @DisplayName("새 비밀번호와 확인 불일치 - PASSWORD_CONFIRM_MISMATCH 예외")
        void changeMyPassword_confirmMismatch_throwsException() {
            User user = buildApprovedUser(1L);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(passwordEncoder.matches("OldPass@1", "encodedOldPassword")).willReturn(true);

            ChangeMyPasswordRequest request = new ChangeMyPasswordRequest();
            setRequestField(request, "currentPassword", "OldPass@1");
            setRequestField(request, "newPassword", "NewPass@1");
            setRequestField(request, "newPasswordConfirm", "DifferentPass@1");

            assertThatThrownBy(() -> myProfileService.changeMyPassword(1L, request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.PASSWORD_CONFIRM_MISMATCH));
        }

        @Test
        @DisplayName("현재 비밀번호와 동일한 새 비밀번호 - SAME_AS_CURRENT_PASSWORD 예외")
        void changeMyPassword_sameAsCurrent_throwsException() {
            User user = buildApprovedUser(1L);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(passwordEncoder.matches("OldPass@1", "encodedOldPassword")).willReturn(true);

            ChangeMyPasswordRequest request = new ChangeMyPasswordRequest();
            setRequestField(request, "currentPassword", "OldPass@1");
            setRequestField(request, "newPassword", "OldPass@1");
            setRequestField(request, "newPasswordConfirm", "OldPass@1");

            // 새 비밀번호와 현재 암호화된 비밀번호 비교 (동일하면 true)
            given(passwordEncoder.matches("OldPass@1", "encodedOldPassword")).willReturn(true);

            assertThatThrownBy(() -> myProfileService.changeMyPassword(1L, request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.SAME_AS_CURRENT_PASSWORD));
        }

        @Test
        @DisplayName("존재하지 않는 사용자 비밀번호 변경 - USER_NOT_FOUND 예외")
        void changeMyPassword_userNotFound_throwsException() {
            given(userRepository.findById(99L)).willReturn(Optional.empty());
            ChangeMyPasswordRequest request = new ChangeMyPasswordRequest();
            setRequestField(request, "currentPassword", "OldPass@1");
            setRequestField(request, "newPassword", "NewPass@1");
            setRequestField(request, "newPasswordConfirm", "NewPass@1");

            assertThatThrownBy(() -> myProfileService.changeMyPassword(99L, request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_FOUND));
        }
    }
}
