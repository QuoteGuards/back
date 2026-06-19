package com.project.back.domain.quote.controller;

import com.project.back.domain.quote.dto.QuoteDetailResponse;
import com.project.back.domain.quote.service.QuoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quotes")
@RequiredArgsConstructor
public class QuoteController {

    private final QuoteService quoteService;

    @GetMapping("/{quoteNumber}")
    public ResponseEntity<QuoteDetailResponse> getQuote(
            @PathVariable String quoteNumber,
            @AuthenticationPrincipal String userId
    ) {
        return ResponseEntity.ok(quoteService.getQuote(quoteNumber, Long.parseLong(userId)));
    }
}
