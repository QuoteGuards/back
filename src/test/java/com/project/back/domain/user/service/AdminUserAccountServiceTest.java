package com.project.back.domain.user.service;

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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 계정 생성 서비스 테스트")
class AdminUserAccountServiceTest {

    @InjectMocks
    private UserManagementService userManagementService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

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
                .mustChangePassword(true)
                .build();
        setField(user, "id", 1L);
        setField(user, "createdAt", LocalDateTime.now());
        setField(user, "updatedAt", LocalDateTime.now());
        return user;
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
        @DisplayName("첫 번째 계정: 현재 연도 + '001' 형식으로 생성")
        void firstAccount_generatesYearPlusSequence() {
            String year = String.valueOf(LocalDate.now().getYear());
            String expectedMemberNo = year + "001";
            AdminCreateUserRequest request = buildRequest(UserRole.SALES_STAFF);

            given(userRepository.findMaxMemberNumberByYearPrefix(year)).willReturn(Optional.empty());
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(userRepository.existsByPhone(anyString())).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("encoded");
            given(userRepository.save(any())).willReturn(buildSavedUser(expectedMemberNo));

            AdminCreateUserResponse result = userManagementService.createUser(request);

            assertThat(result.getMemberNumber()).isEqualTo(expectedMemberNo);
        }

        @Test
        @DisplayName("기존 2026003이 있으면 다음은 2026004로 생성")
        void nextAccount_incrementsSequence() {
            String year = String.valueOf(LocalDate.now().getYear());
            String lastMemberNo = year + "003";
            String expectedMemberNo = year + "004";
            AdminCreateUserRequest request = buildRequest(UserRole.SALES_STAFF);

            given(userRepository.findMaxMemberNumberByYearPrefix(year)).willReturn(Optional.of(lastMemberNo));
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(userRepository.existsByPhone(anyString())).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("encoded");
            given(userRepository.save(any())).willReturn(buildSavedUser(expectedMemberNo));

            AdminCreateUserResponse result = userManagementService.createUser(request);

            assertThat(result.getMemberNumber()).isEqualTo(expectedMemberNo);
        }

        @Test
        @DisplayName("이메일은 {회원번호}@quoteguard.com 형식으로 자동 생성")
        void emailAutoGenerated_fromMemberNumber() {
            String year = String.valueOf(LocalDate.now().getYear());
            String expectedMemberNo = year + "001";
            AdminCreateUserRequest request = buildRequest(UserRole.SALES_STAFF);

            given(userRepository.findMaxMemberNumberByYearPrefix(year)).willReturn(Optional.empty());
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(userRepository.existsByPhone(anyString())).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("encoded");
            given(userRepository.save(any())).willReturn(buildSavedUser(expectedMemberNo));

            AdminCreateUserResponse result = userManagementService.createUser(request);

            assertThat(result.getEmail()).isEqualTo(expectedMemberNo + "@quoteguard.com");
        }

        @Test
        @DisplayName("생성된 계정은 ACTIVE 상태로 즉시 활성화")
        void createdUser_isActiveImmediately() {
            String year = String.valueOf(LocalDate.now().getYear());
            String memberNo = year + "001";
            AdminCreateUserRequest request = buildRequest(UserRole.SALES_STAFF);

            given(userRepository.findMaxMemberNumberByYearPrefix(year)).willReturn(Optional.empty());
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(userRepository.existsByPhone(anyString())).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("encoded");
            given(userRepository.save(any())).willReturn(buildSavedUser(memberNo));

            AdminCreateUserResponse result = userManagementService.createUser(request);

            assertThat(result.getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("임시 비밀번호는 QG- 접두사를 포함한 11자 문자열")
        void temporaryPassword_hasCorrectFormat() {
            String year = String.valueOf(LocalDate.now().getYear());
            String memberNo = year + "001";
            AdminCreateUserRequest request = buildRequest(UserRole.SALES_STAFF);

            given(userRepository.findMaxMemberNumberByYearPrefix(year)).willReturn(Optional.empty());
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(userRepository.existsByPhone(anyString())).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("encoded");
            given(userRepository.save(any())).willReturn(buildSavedUser(memberNo));

            AdminCreateUserResponse result = userManagementService.createUser(request);

            assertThat(result.getTemporaryPassword()).startsWith("QG-");
            assertThat(result.getTemporaryPassword()).hasSize(11); // "QG-" + 8자
        }

        @Test
        @DisplayName("임시 비밀번호는 암호화되어 저장됨 (passwordEncoder.encode 호출)")
        void temporaryPassword_isEncoded() {
            String year = String.valueOf(LocalDate.now().getYear());
            AdminCreateUserRequest request = buildRequest(UserRole.SALES_STAFF);

            given(userRepository.findMaxMemberNumberByYearPrefix(year)).willReturn(Optional.empty());
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(userRepository.existsByPhone(anyString())).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("encoded_pw");
            given(userRepository.save(any())).willReturn(buildSavedUser(year + "001"));

            userManagementService.createUser(request);

            verify(passwordEncoder).encode(anyString());
        }
    }

    // ── 이메일 중복 ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("이메일 중복 검사 (안전망)")
    class EmailDuplicate {

        @Test
        @DisplayName("자동 생성된 이메일이 이미 존재하면 DUPLICATE_EMAIL 예외")
        void duplicateEmail_throwsException() {
            String year = String.valueOf(LocalDate.now().getYear());
            AdminCreateUserRequest request = buildRequest(UserRole.SALES_STAFF);

            given(userRepository.findMaxMemberNumberByYearPrefix(year)).willReturn(Optional.empty());
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
            String year = String.valueOf(LocalDate.now().getYear());
            AdminCreateUserRequest request = buildRequest(UserRole.SALES_STAFF);

            given(userRepository.findMaxMemberNumberByYearPrefix(year)).willReturn(Optional.empty());
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
            String year = String.valueOf(LocalDate.now().getYear());
            AdminCreateUserRequest request = new AdminCreateUserRequest();
            setField(request, "name", "홍길동");
            setField(request, "phone", null);
            setField(request, "role", UserRole.SALES_STAFF);

            given(userRepository.findMaxMemberNumberByYearPrefix(year)).willReturn(Optional.empty());
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("encoded");
            given(userRepository.save(any())).willReturn(buildSavedUser(year + "001"));

            userManagementService.createUser(request);

            verify(userRepository, never()).existsByPhone(anyString());
        }
    }
}
