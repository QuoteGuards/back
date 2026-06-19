package com.project.back.domain.user.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateUserInfoRequest {

    @Size(max = 50, message = "이름은 최대 {max}자까지 입력 가능합니다.")
    private String name;

    @Size(max = 50, message = "부서명은 최대 {max}자까지 입력 가능합니다.")
    private String department;

    @Size(max = 50, message = "직급은 최대 {max}자까지 입력 가능합니다.")
    private String position;

    @Pattern(
            regexp = "^(010-\\d{3,4}-\\d{4})?$",
            message = "올바른 전화번호 형식(010-XXXX-XXXX)이 아닙니다."
    )
    private String phone;
}
