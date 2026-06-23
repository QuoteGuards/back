package com.project.back.ai.service;

import com.project.back.ai.dto.request.ConsultationSummaryRequest;
import com.project.back.ai.dto.request.ProposalMessageRequest;
import com.project.back.ai.dto.response.ConsultationSummaryResponse;
import com.project.back.ai.dto.response.ProposalMessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiService {

    private final RestClient restClient = RestClient.create();

    @Value("${gemini.api-key}")
    private String geminiApiKey;

    public ConsultationSummaryResponse summarizeConsultation(ConsultationSummaryRequest request) {
        String memo = request == null ? "" : safe(request.consultationMemo());

        String prompt = """
                다음 고객 상담 메모를 견적 검토자가 빠르게 이해할 수 있도록 3줄 이내로 요약해줘.
                반드시 한국어로 작성하고, 핵심 요구사항/납기/할인 관련 내용을 중심으로 정리해줘.

                상담 메모:
                %s
                """.formatted(memo);

        String summary = callGemini(prompt);

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
                : request.productNames();

        String prompt = """
                아래 정보를 바탕으로 고객에게 보낼 견적 제안 문구 초안을 작성해줘.
                말투는 정중하고 자연스럽게 작성해줘.
                너무 길지 않게 5~7문장 정도로 작성해줘.

                고객명: %s
                고객사: %s
                상담 메모: %s
                제품 목록: %s
                """.formatted(customerName, customerCompany, memo, String.join(", ", productNames));

        String message = callGemini(prompt);

        return ProposalMessageResponse.builder()
                .customerName(customerName)
                .customerCompany(customerCompany)
                .proposalMessage(message)
                .saved(false)
                .build();
    }

    private String callGemini(String prompt) {
        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                )
        );

        Map response = restClient.post()
                .uri("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + geminiApiKey)
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(Map.class);

        return extractText(response);
    }

    private String extractText(Map response) {
        if (response == null) {
            return "AI 응답을 생성하지 못했습니다.";
        }

        List candidates = (List) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            return "AI 응답을 생성하지 못했습니다.";
        }

        Map candidate = (Map) candidates.get(0);
        Map content = (Map) candidate.get("content");
        List parts = (List) content.get("parts");

        if (parts == null || parts.isEmpty()) {
            return "AI 응답을 생성하지 못했습니다.";
        }

        Map part = (Map) parts.get(0);
        Object text = part.get("text");

        return text == null ? "AI 응답을 생성하지 못했습니다." : text.toString().trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}