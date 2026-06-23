package com.project.back.ai.controller;

import com.project.back.ai.dto.request.ConsultationSummaryRequest;
import com.project.back.ai.dto.request.ProposalMessageRequest;
import com.project.back.ai.dto.response.ConsultationSummaryResponse;
import com.project.back.ai.dto.response.ProposalMessageResponse;
import com.project.back.ai.service.AiService;
import com.project.back.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    // 상담 메모 요약
    @PostMapping("/consultation-summary")
    public ResponseEntity<ApiResponse<ConsultationSummaryResponse>> summarizeConsultation(
            @RequestBody ConsultationSummaryRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "상담 메모 요약 성공",
                aiService.summarizeConsultation(request)
        ));
    }

    // 고객 제안 문구 생성
    @PostMapping("/proposal-message")
    public ResponseEntity<ApiResponse<ProposalMessageResponse>> createProposalMessage(
            @RequestBody ProposalMessageRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "고객 제안 문구 생성 성공",
                aiService.createProposalMessage(request)
        ));
    }
}