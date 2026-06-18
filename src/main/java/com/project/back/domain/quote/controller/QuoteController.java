package com.project.back.domain.quote.controller;

import com.project.back.domain.quote.dto.request.QuoteCreateRequest;
import com.project.back.domain.quote.dto.request.QuoteItemRequest;
import com.project.back.domain.quote.dto.request.QuoteUpdateRequest;
import com.project.back.domain.quote.dto.response.QuoteDetailResponse;
import com.project.back.domain.quote.dto.response.QuoteInternalAnalysisResponse;
import com.project.back.domain.quote.dto.response.QuoteListResponse;
import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.quote.service.QuoteService;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.global.common.ApiResponse;
import com.project.back.global.enums.QuoteStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/quotes")
@RequiredArgsConstructor
public class QuoteController {

    private final QuoteService quoteService;
    private final UserRepository userRepository;

    //견적작성 및 임시저장
    @PostMapping
    public ResponseEntity<ApiResponse<QuoteDetailResponse>> createQuote(
            @AuthenticationPrincipal String userId,
            @RequestBody @Valid QuoteCreateRequest request) {

        Quote quote = quoteService.saveDraft(
                getUser(userId),
                request.customerId(),
                request.discountPolicyId(),
                request.internalMemo(),
                request.validUntil(),
                toCommands(request.items())
        );
        return ResponseEntity.ok(ApiResponse.success("임시저장되었습니다.", QuoteDetailResponse.from(quote)));
    }

    //견적 작성 완료 및 승인 필요 판단
    @PostMapping("/{quoteId}/complete")
    public ResponseEntity<ApiResponse<QuoteDetailResponse>> completeQuote(
            @AuthenticationPrincipal String userId,
            @PathVariable Long quoteId) {

        Quote quote = quoteService.submitQuote(quoteId, getUser(userId));
        return ResponseEntity.ok(ApiResponse.success("견적이 제출되었습니다.", QuoteDetailResponse.from(quote)));
    }

    //견적 수정 (DRAFT / REVISING 상태만 가능)
    @PatchMapping("/{quoteId}")
    public ResponseEntity<ApiResponse<QuoteDetailResponse>> updateQuote(
            @AuthenticationPrincipal String userId,
            @PathVariable Long quoteId,
            @RequestBody @Valid QuoteUpdateRequest request) {

        Quote quote = quoteService.updateQuote(
                quoteId,
                getUser(userId),
                request.customerId(),
                request.internalMemo(),
                request.validUntil(),
                toCommands(request.items())
        );
        return ResponseEntity.ok(ApiResponse.success("견적이 수정되었습니다.", QuoteDetailResponse.from(quote)));
    }

    //내 견적 목록 (다중 조건 검색)
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<QuoteListResponse>>> getMyQuotes(
            @AuthenticationPrincipal String userId,
            @RequestParam(required = false) QuoteStatus status,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String quoteNumber,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        List<QuoteListResponse> result = quoteService
                .searchMyQuotes(Long.parseLong(userId), status, customerName, quoteNumber, from, to)
                .stream()
                .map(QuoteListResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    //견적 상세 조회
    @GetMapping("/{quoteId}")
    public ResponseEntity<ApiResponse<QuoteDetailResponse>> getQuoteDetail(
            @AuthenticationPrincipal String userId,
            @PathVariable Long quoteId) {

        Quote quote = quoteService.getQuoteDetail(quoteId, getUser(userId));
        return ResponseEntity.ok(ApiResponse.success(QuoteDetailResponse.from(quote)));
    }

    //내부 견적 분석 조회 (원가/이익률 포함)
    @GetMapping("/{quoteId}/internal-analysis")
    public ResponseEntity<ApiResponse<QuoteInternalAnalysisResponse>> getInternalAnalysis(
            @AuthenticationPrincipal String userId,
            @PathVariable Long quoteId) {

        Quote quote = quoteService.getInternalAnalysis(quoteId, getUser(userId));
        return ResponseEntity.ok(ApiResponse.success(QuoteInternalAnalysisResponse.from(quote)));
    }

    //최근 견적 재사용
    @PostMapping("/{quoteId}/reuse")
    public ResponseEntity<ApiResponse<QuoteDetailResponse>> reuseQuote(
            @AuthenticationPrincipal String userId,
            @PathVariable Long quoteId) {

        Quote quote = quoteService.reuseQuote(quoteId, getUser(userId));
        return ResponseEntity.ok(ApiResponse.success("견적이 재사용되었습니다.", QuoteDetailResponse.from(quote)));
    }

    //만료 견적 재작성 (버전 증가)
    @PostMapping("/{quoteId}/rewrite")
    public ResponseEntity<ApiResponse<QuoteDetailResponse>> rewriteQuote(
            @AuthenticationPrincipal String userId,
            @PathVariable Long quoteId) {

        Quote quote = quoteService.rewriteExpiredQuote(quoteId, getUser(userId));
        return ResponseEntity.ok(ApiResponse.success("견적이 재작성되었습니다.", QuoteDetailResponse.from(quote)));
    }

    private User getUser(String userId) {
        return userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
    }

    private List<QuoteService.QuoteItemCommand> toCommands(List<QuoteItemRequest> items) {
        return items.stream()
                .map(i -> new QuoteService.QuoteItemCommand(
                        i.productId(),
                        i.productName(),
                        i.productCode(),
                        i.unitPrice(),
                        i.costPrice(),
                        i.quantity(),
                        i.discountRate(),
                        i.vatApplicable()
                ))
                .toList();
    }
}
