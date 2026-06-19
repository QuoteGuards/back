package com.project.back.domain.auth.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignUpRequest {

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    @Size(max = 100, message = "이메일은 최대 {max}자까지 입력 가능합니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,20}$",
            message = "비밀번호는 8자 이상 20자 이하의 영문, 숫자, 특수문자 조합이어야 합니다."
    )
    private String password;

    @NotBlank(message = "이름은 필수입니다.")
    @Size(max = 50, message = "이름은 최대 {max}자까지 입력 가능합니다.")
    private String name;

    @Pattern(
            regexp = "^(010-\\d{3,4}-\\d{4})?$",
            message = "올바른 전화번호 형식(010-XXXX-XXXX)이 아닙니다."
    )
    private String phone;
}
