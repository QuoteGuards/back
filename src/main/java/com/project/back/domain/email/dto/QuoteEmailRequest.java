package com.project.back.domain.email.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record QuoteEmailRequest(
        @NotBlank(message = "받는 사람은 필수입니다.")
        @Email(message = "받는 사람 이메일 형식이 올바르지 않습니다.")
        String to,

        @Email(message = "참조 이메일 형식이 올바르지 않습니다.")
        String cc,

        @NotBlank(message = "제목은 필수입니다.")
        String subject,

        String body,

        boolean attachPdf
) {}
