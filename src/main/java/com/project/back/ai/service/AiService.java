package com.project.back.ai.service;

import com.project.back.ai.dto.request.ConsultationSummaryRequest;
import com.project.back.ai.dto.request.ProposalMessageRequest;
import com.project.back.ai.dto.response.ConsultationSummaryResponse;
import com.project.back.ai.dto.response.ProposalMessageResponse;
import com.project.back.global.client.GeminiClient;
import com.project.back.global.client.GroqClient;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiService {

    private final GeminiClient geminiClient;
    private final GroqClient groqClient;

    public ConsultationSummaryResponse summarizeConsultation(ConsultationSummaryRequest request) {
        String memo = request == null ? "" : safe(request.consultationMemo());

        String prompt = """
                다음 고객 상담 메모를 견적 검토자가 빠르게 이해할 수 있도록 3줄 이내로 요약해줘.
                반드시 한국어로 작성하고, 핵심 요구사항/납기/할인 관련 내용을 중심으로 정리해줘.

                상담 메모:
                %s
                """.formatted(memo);

        String summary = callAi(prompt);

        return ConsultationSummaryResponse.builder()
                .originalMemo(memo)
                .summary(summary)
                .saved(false)
                .build();
    }

    public ProposalMessageResponse createProposalMessage(ProposalMessageRequest request) {
        String customerName = request == null ? "" : safe(request.customerName());
        String customerCompany = request == null ? "" : safe(request.customerCompany());
        String memo = request == null ? "" : safe(request.consultationMemo());
        List<String> productNames = request == null || request.productNames() == null
                ? List.of()
                : request.productNames().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .toList();

        String prompt = """
                아래 정보를 바탕으로 고객에게 보낼 견적 제안 문구 초안을 작성해줘.
                말투는 정중하고 자연스럽게 작성해줘.
                너무 길지 않게 5~7문장 정도로 작성해줘.

                고객명: %s
                고객사: %s
                상담 메모: %s
                제품 목록: %s
                """.formatted(customerName, customerCompany, memo, String.join(", ", productNames));

        String message = callAi(prompt);

        return ProposalMessageResponse.builder()
                .customerName(customerName)
                .customerCompany(customerCompany)
                .proposalMessage(message)
                .saved(false)
                .build();
    }

    // Gemini 호출, 호출 한도 초과(429) 시 Groq로 대체 (AiRiskSummaryService와 동일한 폴백 정책)
    // 최종 실패 시에는 approval 도메인 문구가 섞이지 않도록 AI 도메인 전용 오류로 변환해 던진다.
    private String callAi(String prompt) {
        try {
            return geminiClient.generateContent(prompt);
        } catch (CustomException e) {
            if (e.getErrorCode() != ErrorCode.AI_SUMMARY_RATE_LIMITED) {
                throw new CustomException(ErrorCode.AI_GENERATION_FAILED);
            }
            try {
                return groqClient.generateContent(prompt);
            } catch (CustomException fallbackError) {
                throw new CustomException(ErrorCode.AI_GENERATION_FAILED);
            }
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}