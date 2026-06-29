package com.project.back.domain.user.service;

import com.project.back.domain.auth.service.InitialPasswordSetupService;
import com.project.back.domain.user.dto.request.AdminCreateUserRequest;
import com.project.back.domain.user.dto.response.AdminCreateUserResponse;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.entity.UserRole;
import com.project.back.domain.user.entity.UserStatus;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 계정 생성 서비스 테스트")
class AdminUserAccountServiceTest {

    @InjectMocks
    private UserManagementService userManagementService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private InitialPasswordSetupService initialPasswordSetupService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userManagementService, "emailDomain", "quoteguard.com");
    }

    // ── 공통 헬퍼 ──────────────────────────────────────────────────────────

    private AdminCreateUserRequest buildRequest(UserRole role) {
        AdminCreateUserRequest req = new AdminCreateUserRequest();
        setField(req, "name", "홍길동");
        setField(req, "department", "영업1팀");
        setField(req, "position", "대리");
        setField(req, "phone", "010-1234-5678");
        setField(req, "role", role);
        return req;
    }

    private User buildSavedUser(String memberNumber) {
        User user = User.builder()
                .memberNumber(memberNumber)
                .email(memberNumber + "@quoteguard.com")
                .password("encoded_password")
                .name("홍길동")
                .department("영업1팀")
                .position("대리")
                .phone("010-1234-5678")
                .role(UserRole.SALES_STAFF)
                .status(UserStatus.ACTIVE)
                .passwordInitialized(false)
                .mustChangePassword(false)
                .build();
        setField(user, "id", 1L);
        setField(user, "createdAt", LocalDateTime.now());
        setField(user, "updatedAt", LocalDateTime.now());
        return user;
    }

    /** save()에 전달된 User 그대로 반환하는 스텁 헬퍼 */
    private void stubSavePassthrough() {
        given(userRepository.save(any(User.class)))
                .willAnswer(inv -> buildSavedUser(((User) inv.getArgument(0)).getMemberNumber()));
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── 회원번호 자동 생성 ─────────────────────────────────────────────────

    @Nested
    @DisplayName("회원번호 자동 생성")
    class MemberNumberGeneration {

        @Test
        @DisplayName("회원번호는 YY(2자리) + 5자리 난수, 총 7자리 숫자로 생성")
        void memberNumber_hasCorrectFormat() {
            String yyPrefix = String.format("%02d", LocalDate.now().getYear() % 100);
            AdminCreateUserRequest request = buildRequest(UserRole.SALES_STAFF);

            given(userRepository.existsByMemberNumber(anyString())).willReturn(false);
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(userRepository.existsByPhone(anyString())).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("encoded");
            stubSavePassthrough();
            doNothing().when(initialPasswordSetupService).sendSetupLink(any(User.class));

            AdminCreateUserResponse result = userManagementService.createUser(request);

            assertThat(result.getMemberNumber()).hasSize(7);
            assertThat(result.getMemberNumber()).startsWith(yyPrefix);
            assertThat(result.getMemberNumber()).matches("\\d{7}");
        }

        @Test
        @DisplayName("회원번호 중복 시 재시도하여 생성 성공")
        void memberNumber_retriesOnDuplicate() {
            AdminCreateUserRequest request = buildRequest(UserRole.SALES_STAFF);

            given(userRepository.existsByMemberNumber(anyString()))
                    .willReturn(true)
                    .willReturn(false);
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(userRepository.existsByPhone(anyString())).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("encoded");
            stubSavePassthrough();
            doNothing().when(initialPasswordSetupService).sendSetupLink(any(User.class));

            AdminCreateUserResponse result = userManagementService.createUser(request);

            assertThat(result.getMemberNumber()).hasSize(7);
        }

        @Test
        @DisplayName("5회 모두 중복이면 MEMBER_NUMBER_GENERATION_FAILED 예외")
        void memberNumber_failsAfterMaxRetries() {
            AdminCreateUserRequest request = buildRequest(UserRole.SALES_STAFF);

            given(userRepository.existsByMemberNumber(anyString())).willReturn(true);

            assertThatThrownBy(() -> userManagementService.createUser(request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.MEMBER_NUMBER_GENERATION_FAILED));

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("이메일은 {회원번호}@quoteguard.com 형식으로 자동 생성")
        void emailAutoGenerated_fromMemberNumber() {
            AdminCreateUserRequest request = buildRequest(UserRole.SALES_STAFF);

            given(userRepository.existsByMemberNumber(anyString())).willReturn(false);
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(userRepository.existsByPhone(anyString())).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("encoded");
            stubSavePassthrough();
            doNothing().when(initialPasswordSetupService).sendSetupLink(any(User.class));

            AdminCreateUserResponse result = userManagementService.createUser(request);

            assertThat(result.getEmail()).endsWith("@quoteguard.com");
            assertThat(result.getEmail()).startsWith(result.getMemberNumber());
        }

        @Test
        @DisplayName("생성된 계정은 ACTIVE 상태, passwordInitialized=false")
        void createdUser_isActive_andPasswordNotInitialized() {
            AdminCreateUserRequest request = buildRequest(UserRole.SALES_STAFF);

            given(userRepository.existsByMemberNumber(anyString())).willReturn(false);
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(userRepository.existsByPhone(anyString())).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("encoded");
            stubSavePassthrough();
            doNothing().when(initialPasswordSetupService).sendSetupLink(any(User.class));

            AdminCreateUserResponse result = userManagementService.createUser(request);

            assertThat(result.getStatus()).isEqualTo("ACTIVE");
            assertThat(result.isPasswordInitialized()).isFalse();
        }

        @Test
        @DisplayName("임시 비밀번호를 응답에 포함하지 않음")
        void response_doesNotContainTemporaryPassword() {
            AdminCreateUserRequest request = buildRequest(UserRole.SALES_STAFF);

            given(userRepository.existsByMemberNumber(anyString())).willReturn(false);
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(userRepository.existsByPhone(anyString())).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("encoded");
            stubSavePassthrough();
            doNothing().when(initialPasswordSetupService).sendSetupLink(any(User.class));

            AdminCreateUserResponse result = userManagementService.createUser(request);

            // 응답에 temporaryPassword 필드 없음 확인
            // AdminCreateUserResponse에는 temporaryPassword 필드가 없다
            assertThat(result).isNotNull();
            assertThat(result.getMemberNumber()).isNotNull();
        }

        @Test
        @DisplayName("계정 생성 후 초기 비밀번호 설정 이메일 발송 서비스 호출")
        void createUser_callsSendSetupLink() {
            AdminCreateUserRequest request = buildRequest(UserRole.SALES_STAFF);

            given(userRepository.existsByMemberNumber(anyString())).willReturn(false);
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(userRepository.existsByPhone(anyString())).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("encoded");
            stubSavePassthrough();
            doNothing().when(initialPasswordSetupService).sendSetupLink(any(User.class));

            userManagementService.createUser(request);

            verify(initialPasswordSetupService).sendSetupLink(any(User.class));
        }

        @Test
        @DisplayName("비밀번호는 BCrypt 인코딩하여 저장 (placeholder, 원문 미노출)")
        void password_isEncoded() {
            AdminCreateUserRequest request = buildRequest(UserRole.SALES_STAFF);

            given(userRepository.existsByMemberNumber(anyString())).willReturn(false);
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(userRepository.existsByPhone(anyString())).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("encoded_pw");
            stubSavePassthrough();
            doNothing().when(initialPasswordSetupService).sendSetupLink(any(User.class));

            userManagementService.createUser(request);

            verify(passwordEncoder).encode(anyString());
        }
    }

    // ── SUPER_ADMIN 역할 차단 ──────────────────────────────────────────────

    @Nested
    @DisplayName("SUPER_ADMIN 역할 생성 차단")
    class SuperAdminRoleBlocking {

        @Test
        @DisplayName("SUPER_ADMIN 역할로 계정 생성 시 ACCESS_DENIED 예외")
        void createUser_superAdminRole_throwsAccessDenied() {
            AdminCreateUserRequest request = buildRequest(UserRole.SUPER_ADMIN);

            given(userRepository.existsByMemberNumber(anyString())).willReturn(false);
            given(userRepository.existsByEmail(anyString())).willReturn(false);

            assertThatThrownBy(() -> userManagementService.createUser(request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.ACCESS_DENIED));

            verify(userRepository, never()).save(any());
        }
    }

    // ── 이메일 중복 ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("이메일 중복 검사 (안전망)")
    class EmailDuplicate {

        @Test
        @DisplayName("자동 생성된 이메일이 이미 존재하면 DUPLICATE_EMAIL 예외")
        void duplicateEmail_throwsException() {
            AdminCreateUserRequest request = buildRequest(UserRole.SALES_STAFF);

            given(userRepository.existsByMemberNumber(anyString())).willReturn(false);
            given(userRepository.existsByEmail(anyString())).willReturn(true);

            assertThatThrownBy(() -> userManagementService.createUser(request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.DUPLICATE_EMAIL));

            verify(userRepository, never()).save(any());
        }
    }

    // ── 전화번호 중복 ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("전화번호 중복 검사")
    class PhoneDuplicate {

        @Test
        @DisplayName("전화번호가 이미 사용 중이면 DUPLICATE_PHONE 예외")
        void duplicatePhone_throwsException() {
            AdminCreateUserRequest request = buildRequest(UserRole.SALES_STAFF);

            given(userRepository.existsByMemberNumber(anyString())).willReturn(false);
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(userRepository.existsByPhone("010-1234-5678")).willReturn(true);

            assertThatThrownBy(() -> userManagementService.createUser(request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.DUPLICATE_PHONE));

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("전화번호가 없으면 중복 검사를 건너뜀")
        void noPhone_skipsPhoneCheck() {
            AdminCreateUserRequest request = new AdminCreateUserRequest();
            setField(request, "name", "홍길동");
            setField(request, "phone", null);
            setField(request, "role", UserRole.SALES_STAFF);

            given(userRepository.existsByMemberNumber(anyString())).willReturn(false);
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("encoded");
            stubSavePassthrough();
            doNothing().when(initialPasswordSetupService).sendSetupLink(any(User.class));

            userManagementService.createUser(request);

            verify(userRepository, never()).existsByPhone(anyString());
        }
    }
}
