package com.project.back.domain.approval.service;

import com.project.back.domain.approval.dto.response.AiRiskSummaryResponse;
import com.project.back.domain.approval.entity.ApprovalRequest;
import com.project.back.domain.approval.entity.QuoteApprovalReason;
import com.project.back.domain.approval.repository.ApprovalRequestRepository;
import com.project.back.domain.approval.repository.QuoteApprovalReasonRepository;
import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.quote.entity.QuoteItem;
import com.project.back.domain.quote.repository.QuoteItemRepository;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.global.client.GeminiClient;
import com.project.back.global.client.GroqClient;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("AiRiskSummaryService 단위 테스트")
class AiRiskSummaryServiceTest {

    private ApprovalRequestRepository approvalRequestRepository;
    private QuoteItemRepository quoteItemRepository;
    private QuoteApprovalReasonRepository quoteApprovalReasonRepository;
    private UserRepository userRepository;
    private GeminiClient geminiClient;
    private GroqClient groqClient;
    private AiRiskSummaryService service;

    @BeforeEach
    void setUp() {
        approvalRequestRepository = mock(ApprovalRequestRepository.class);
        quoteItemRepository = mock(QuoteItemRepository.class);
        quoteApprovalReasonRepository = mock(QuoteApprovalReasonRepository.class);
        userRepository = mock(UserRepository.class);
        geminiClient = mock(GeminiClient.class);
        groqClient = mock(GroqClient.class);
        service = new AiRiskSummaryService(
                approvalRequestRepository,
                quoteItemRepository,
                quoteApprovalReasonRepository,
                userRepository,
                geminiClient,
                groqClient
        );
    }

    private ApprovalRequest mockApprovalRequest(String existingSummary) {
        Quote quote = mock(Quote.class);
        when(quote.getId()).thenReturn(1L);
        when(quote.getTotalAmount()).thenReturn(new BigDecimal("12000000"));
        when(quote.getDiscountAmount()).thenReturn(new BigDecimal("3200000"));
        when(quote.getSupplyAmount()).thenReturn(new BigDecimal("8800000"));
        when(quote.getProfitRate()).thenReturn(new BigDecimal("8.20"));
        when(quote.getExpectedProfitAmount()).thenReturn(new BigDecimal("720000"));

        ApprovalRequest request = mock(ApprovalRequest.class);
        when(request.getId()).thenReturn(1L);
        when(request.getAiRiskSummary()).thenReturn(existingSummary);
        when(request.getQuote()).thenReturn(quote);
        when(request.getHistories()).thenReturn(List.of());

        return request;
    }

    @Nested
    @DisplayName("getSummary - SUPER_ADMIN")
    class GetSummaryTests {

        @Test
        @DisplayName("이미 저장된 요약이 있으면 Gemini 호출 없이 cached:true 반환")
        void returnsCachedSummary() {
            ApprovalRequest request = mockApprovalRequest("- 기존 요약 내용");
            when(approvalRequestRepository.findByIdWithUsers(1L)).thenReturn(Optional.of(request));

            AiRiskSummaryResponse result = service.getSummary(1L);

            assertThat(result.isCached()).isTrue();
            assertThat(result.getAiRiskSummary()).isEqualTo("- 기존 요약 내용");
            verify(geminiClient, never()).generateContent(anyString());
        }

        @Test
        @DisplayName("저장된 요약이 없으면 Gemini 호출 후 cached:false 반환")
        void generatesNewSummary() {
            ApprovalRequest request = mockApprovalRequest(null);
            when(approvalRequestRepository.findByIdWithUsers(1L)).thenReturn(Optional.of(request));
            when(quoteItemRepository.findByQuoteIdOrderBySortOrderAsc(1L)).thenReturn(List.of());
            when(quoteApprovalReasonRepository.findByQuote_Id(1L)).thenReturn(List.of());
            when(geminiClient.generateContent(anyString())).thenReturn("- 새 요약");

            AiRiskSummaryResponse result = service.getSummary(1L);

            assertThat(result.isCached()).isFalse();
            assertThat(result.getAiRiskSummary()).isEqualTo("- 새 요약");
            verify(geminiClient, times(1)).generateContent(anyString());
            verify(request, times(1)).updateAiRiskSummary("- 새 요약");
        }

        @Test
        @DisplayName("승인 요청이 없으면 APPROVAL_REQUEST_NOT_FOUND 예외")
        void throwsWhenNotFound() {
            when(approvalRequestRepository.findByIdWithUsers(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getSummary(99L))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPROVAL_REQUEST_NOT_FOUND);
        }

        @Test
        @DisplayName("Gemini 실패 시 AI_SUMMARY_GENERATION_FAILED 예외")
        void throwsWhenGeminiFails() {
            ApprovalRequest request = mockApprovalRequest(null);
            when(approvalRequestRepository.findByIdWithUsers(1L)).thenReturn(Optional.of(request));
            when(quoteItemRepository.findByQuoteIdOrderBySortOrderAsc(1L)).thenReturn(List.of());
            when(quoteApprovalReasonRepository.findByQuote_Id(1L)).thenReturn(List.of());
            when(geminiClient.generateContent(anyString()))
                    .thenThrow(new CustomException(ErrorCode.AI_SUMMARY_GENERATION_FAILED));

            assertThatThrownBy(() -> service.getSummary(1L))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AI_SUMMARY_GENERATION_FAILED);
        }
    }

    @Nested
    @DisplayName("getSummaryForManager - SALES_MANAGER")
    class GetSummaryForManagerTests {

        @Test
        @DisplayName("다른 부서 요청이면 APPROVAL_DEPT_MISMATCH 예외")
        void throwsOnDeptMismatch() {
            User manager = mock(User.class);
            when(manager.getDepartment()).thenReturn("영업1팀");

            User requester = mock(User.class);
            when(requester.getDepartment()).thenReturn("영업2팀");

            ApprovalRequest request = mockApprovalRequest(null);
            when(request.getRequester()).thenReturn(requester);

            when(approvalRequestRepository.findByIdWithUsers(1L)).thenReturn(Optional.of(request));
            when(userRepository.findById(10L)).thenReturn(Optional.of(manager));

            assertThatThrownBy(() -> service.getSummaryForManager(1L, 10L))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPROVAL_DEPT_MISMATCH);
        }
    }
}
