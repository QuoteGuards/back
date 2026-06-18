package com.project.back.domain.customer.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerCreateRequest(

        @NotBlank(message = "고객(회사)명은 필수입니다.")
        @Size(max = 200)
        String companyName,

        @NotBlank(message = "담당자명은 필수입니다.")
        @Size(max = 100)
        String contactName,

        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 200)
        String email,

        @Size(max = 20)
        String phone,

        @Size(max = 20)
        String businessNumber,

        @Size(max = 500)
        String address,

        String memo
) {}
