package com.project.back.domain.quote.controller;

import com.project.back.domain.quote.dto.response.AdminQuoteListResponse;
import com.project.back.domain.quote.service.QuoteService;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.global.common.ApiResponse;
import com.project.back.global.enums.QuoteStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/quotes")
@RequiredArgsConstructor
public class AdminQuoteController {

    private final QuoteService quoteService;
    private final UserRepository userRepository;

    // 전체 견적 목록(SUPER_ADMIN 전용)
    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminQuoteListResponse>>> searchAdminQuotes(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) QuoteStatus status,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String quoteNumber,
            @RequestParam(required = false) String writerName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        List<AdminQuoteListResponse> result = quoteService.searchAdminQuotes(
                getUser(userId), status, customerName, quoteNumber, writerName, from, to);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
    }
}
