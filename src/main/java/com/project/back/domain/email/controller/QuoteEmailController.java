package com.project.back.domain.email.controller;

import com.project.back.domain.email.dto.QuoteEmailRequest;
import com.project.back.domain.email.service.QuoteEmailService;
import com.project.back.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quotes")
@RequiredArgsConstructor
public class QuoteEmailController {

    private final QuoteEmailService quoteEmailService;

    // 견적서 이메일 발송 (PDF 첨부 옵션)
    @PostMapping("/{quoteNumber}/email")
    public ResponseEntity<ApiResponse<Void>> sendQuoteEmail(
            @AuthenticationPrincipal Long userId,
            @PathVariable String quoteNumber,
            @RequestBody @Valid QuoteEmailRequest request) {

        quoteEmailService.sendQuoteEmail(quoteNumber, userId, request);
        return ResponseEntity.ok(ApiResponse.success("이메일이 발송되었습니다.", null));
    }
}
