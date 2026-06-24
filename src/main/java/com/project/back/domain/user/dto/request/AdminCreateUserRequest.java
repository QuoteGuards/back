package com.project.back.domain.user.dto.request;

import com.project.back.domain.user.entity.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/**
 * 관리자가 신규 사원 계정 생성 시 입력하는 정보.
 * 사원번호와 이메일은 시스템이 자동 생성하므로 포함하지 않는다.
 */
@Getter
public class AdminCreateUserRequest {

    @NotBlank(message = "이름은 필수입니다.")
    @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
    private String name;

    @Size(max = 50, message = "부서명은 50자 이하여야 합니다.")
    private String department;

    @Size(max = 50, message = "직급은 50자 이하여야 합니다.")
    private String position;

    @Size(max = 20, message = "연락처는 20자 이하여야 합니다.")
    private String phone;

    @NotNull(message = "권한은 필수입니다.")
    private UserRole role;
}
