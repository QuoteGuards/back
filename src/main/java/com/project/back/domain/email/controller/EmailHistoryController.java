package com.project.back.domain.email.controller;

import com.project.back.domain.email.dto.EmailHistoryResponse;
import com.project.back.domain.email.service.EmailHistoryService;
import com.project.back.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/email-history")
@RequiredArgsConstructor
public class EmailHistoryController {

    private final EmailHistoryService emailHistoryService;

    // 내 이메일 발송 이력 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<EmailHistoryResponse>>> getMyHistory(
            @AuthenticationPrincipal Long userId) {

        return ResponseEntity.ok(ApiResponse.success(emailHistoryService.getMyHistory(userId)));
    }
}
