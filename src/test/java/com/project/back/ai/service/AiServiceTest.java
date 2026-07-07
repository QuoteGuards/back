package com.project.back.ai.service;

import com.project.back.ai.dto.request.ConsultationSummaryRequest;
import com.project.back.ai.dto.request.ProposalMessageRequest;
import com.project.back.ai.dto.response.ConsultationSummaryResponse;
import com.project.back.ai.dto.response.ProposalMessageResponse;
import com.project.back.global.client.GeminiClient;
import com.project.back.global.client.GroqClient;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("AiService 단위 테스트")
class AiServiceTest {

    private GeminiClient geminiClient;
    private GroqClient groqClient;
    private AiService service;

    @BeforeEach
    void setUp() {
        geminiClient = mock(GeminiClient.class);
        groqClient = mock(GroqClient.class);
        service = new AiService(geminiClient, groqClient);
    }

    @Nested
    @DisplayName("summarizeConsultation")
    class SummarizeConsultationTests {

        @Test
        @DisplayName("Gemini 정상 응답 시 Groq를 호출하지 않고 결과를 반환한다")
        void returnsGeminiSummaryWhenSucceeds() {
            when(geminiClient.generateContent(anyString())).thenReturn("- 요약 결과");

            ConsultationSummaryResponse result = service.summarizeConsultation(
                    new ConsultationSummaryRequest("고객이 납기 단축을 요청함")
            );

            assertThat(result.summary()).isEqualTo("- 요약 결과");
            assertThat(result.saved()).isFalse();
            verify(groqClient, never()).generateContent(anyString());
        }

        @Test
        @DisplayName("Gemini 호출 한도 초과(AI_SUMMARY_RATE_LIMITED) 시 Groq로 폴백해 결과를 반환한다")
        void fallsBackToGroqWhenGeminiRateLimited() {
            when(geminiClient.generateContent(anyString()))
                    .thenThrow(new CustomException(ErrorCode.AI_SUMMARY_RATE_LIMITED));
            when(groqClient.generateContent(anyString())).thenReturn("- Groq 요약 결과");

            ConsultationSummaryResponse result = service.summarizeConsultation(
                    new ConsultationSummaryRequest("고객이 납기 단축을 요청함")
            );

            assertThat(result.summary()).isEqualTo("- Groq 요약 결과");
            verify(groqClient, times(1)).generateContent(anyString());
        }

        @Test
        @DisplayName("Gemini가 한도 초과가 아닌 다른 사유로 실패하면 Groq를 호출하지 않고 AI_GENERATION_FAILED 예외")
        void throwsWithoutFallbackWhenGeminiFailsForOtherReason() {
            when(geminiClient.generateContent(anyString()))
                    .thenThrow(new CustomException(ErrorCode.AI_SUMMARY_GENERATION_FAILED));

            assertThatThrownBy(() -> service.summarizeConsultation(
                    new ConsultationSummaryRequest("고객이 납기 단축을 요청함")
            ))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AI_GENERATION_FAILED);

            verify(groqClient, never()).generateContent(anyString());
        }

        @Test
        @DisplayName("Gemini 한도 초과 후 Groq마저 실패하면 AI_GENERATION_FAILED 예외")
        void throwsWhenBothProvidersFail() {
            when(geminiClient.generateContent(anyString()))
                    .thenThrow(new CustomException(ErrorCode.AI_SUMMARY_RATE_LIMITED));
            when(groqClient.generateContent(anyString()))
                    .thenThrow(new CustomException(ErrorCode.AI_SUMMARY_GENERATION_FAILED));

            assertThatThrownBy(() -> service.summarizeConsultation(
                    new ConsultationSummaryRequest("고객이 납기 단축을 요청함")
            ))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AI_GENERATION_FAILED);
        }
    }

    @Nested
    @DisplayName("createProposalMessage")
    class CreateProposalMessageTests {

        @Test
        @DisplayName("Gemini 정상 응답 시 제안 문구를 반환한다")
        void returnsProposalMessageWhenSucceeds() {
            when(geminiClient.generateContent(anyString())).thenReturn("안녕하세요, 제안 드립니다.");

            ProposalMessageResponse result = service.createProposalMessage(
                    new ProposalMessageRequest("홍길동", "테스트상사", "가격 문의", List.of("제품A"))
            );

            assertThat(result.proposalMessage()).isEqualTo("안녕하세요, 제안 드립니다.");
            assertThat(result.customerName()).isEqualTo("홍길동");
            verify(groqClient, never()).generateContent(anyString());
        }

        @Test
        @DisplayName("Gemini 호출 한도 초과 시 Groq로 폴백한다")
        void fallsBackToGroqWhenGeminiRateLimited() {
            when(geminiClient.generateContent(anyString()))
                    .thenThrow(new CustomException(ErrorCode.AI_SUMMARY_RATE_LIMITED));
            when(groqClient.generateContent(anyString())).thenReturn("Groq가 생성한 제안 문구");

            ProposalMessageResponse result = service.createProposalMessage(
                    new ProposalMessageRequest("홍길동", "테스트상사", "가격 문의", List.of("제품A"))
            );

            assertThat(result.proposalMessage()).isEqualTo("Groq가 생성한 제안 문구");
        }
    }
}
