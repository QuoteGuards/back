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
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiRiskSummaryService {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final QuoteItemRepository quoteItemRepository;
    private final QuoteApprovalReasonRepository quoteApprovalReasonRepository;
    private final UserRepository userRepository;
    private final GeminiClient geminiClient;

    // SUPER_ADMIN용: 부서 제한 없음
    @Transactional
    public AiRiskSummaryResponse getSummary(Long approvalRequestId) {
        ApprovalRequest approvalRequest = findApprovalRequest(approvalRequestId);
        return generateOrGetCached(approvalRequest);
    }

    // SALES_MANAGER용: 동일 부서만 허용
    @Transactional
    public AiRiskSummaryResponse getSummaryForManager(Long approvalRequestId, Long managerId) {
        ApprovalRequest approvalRequest = findApprovalRequest(approvalRequestId);

        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String managerDept = manager.getDepartment();
        String requesterDept = approvalRequest.getRequester().getDepartment();

        if (managerDept == null || !managerDept.equals(requesterDept)) {
            throw new CustomException(ErrorCode.APPROVAL_DEPT_MISMATCH);
        }

        return generateOrGetCached(approvalRequest);
    }

    private AiRiskSummaryResponse generateOrGetCached(ApprovalRequest approvalRequest) {
        // 이미 생성된 요약이 있으면 캐시 반환
        if (approvalRequest.getAiRiskSummary() != null) {
            return AiRiskSummaryResponse.cached(
                    approvalRequest.getId(),
                    approvalRequest.getAiRiskSummary()
            );
        }

        // 견적 데이터 로딩
        Quote quote = approvalRequest.getQuote();
        List<QuoteItem> items = quoteItemRepository.findByQuoteIdOrderBySortOrderAsc(quote.getId());
        List<QuoteApprovalReason> reasons = quoteApprovalReasonRepository.findByQuote_Id(quote.getId());

        // 프롬프트 조립 및 Gemini 호출
        String prompt = buildPrompt(quote, items, reasons);
        String summary = geminiClient.generateContent(prompt);

        // 저장
        approvalRequest.updateAiRiskSummary(summary);
        approvalRequestRepository.save(approvalRequest);

        return AiRiskSummaryResponse.generated(approvalRequest.getId(), summary);
    }

    private String buildPrompt(Quote quote, List<QuoteItem> items, List<QuoteApprovalReason> reasons) {
        StringBuilder sb = new StringBuilder();
        sb.append("당신은 B2B 영업 견적 리스크 분석 전문가입니다.\n");
        sb.append("아래 견적 정보를 분석하여 승인자가 빠르게 판단할 수 있도록 ");
        sb.append("bullet point 형식으로 리스크를 요약해 주세요. 한국어로 작성하세요.\n\n");

        sb.append("[견적 요약]\n");
        sb.append("- 총 견적액: ").append(quote.getTotalAmount()).append("원\n");
        sb.append("- 할인금액: ").append(quote.getDiscountAmount()).append("원\n");
        sb.append("- 공급가액: ").append(quote.getSupplyAmount()).append("원\n");
        sb.append("- 이익률: ").append(quote.getProfitRate()).append("%\n");
        sb.append("- 예상 이익: ").append(quote.getExpectedProfitAmount()).append("원\n");

        if (!reasons.isEmpty()) {
            sb.append("- 승인 필요 사유: ");
            sb.append(reasons.stream()
                    .map(r -> r.getReasonMessage())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("없음"));
            sb.append("\n");
        }

        if (!items.isEmpty()) {
            sb.append("\n[품목 상세]\n");
            for (int i = 0; i < items.size(); i++) {
                QuoteItem item = items.get(i);
                sb.append(i + 1).append(". ").append(item.getProductName())
                        .append(" (단가 ").append(item.getUnitPrice())
                        .append(" × ").append(item.getQuantity()).append("개")
                        .append(", 할인율 ").append(item.getDiscountRate()).append("%");
                if (item.getDiscountReason() != null && !item.getDiscountReason().isBlank()) {
                    sb.append(", 할인사유: ").append(item.getDiscountReason());
                }
                sb.append(")\n");
            }
        }

        sb.append("\n[출력 형식]\n");
        sb.append("- 항목명: 수치 (판단 코멘트)\n");
        sb.append("마지막 줄에 종합 의견 한 줄\n");

        return sb.toString();
    }

    private ApprovalRequest findApprovalRequest(Long approvalRequestId) {
        return approvalRequestRepository.findByIdWithUsers(approvalRequestId)
                .orElseThrow(() -> new CustomException(ErrorCode.APPROVAL_REQUEST_NOT_FOUND));
    }
}
