package com.project.back.domain.approval.service;

import com.project.back.domain.approval.dto.response.AiRiskSummaryResponse;
import com.project.back.domain.approval.entity.ApprovalRequest;
import com.project.back.domain.approval.entity.QuoteApprovalHistory;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiRiskSummaryService {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final QuoteItemRepository quoteItemRepository;
    private final QuoteApprovalReasonRepository quoteApprovalReasonRepository;
    private final UserRepository userRepository;
    private final GeminiClient geminiClient;
    private final GroqClient groqClient;

    // 저사양 fallback 모델(Groq)이 드물게 섞어내는 한자/일본어 가나/베트남어 성조모음 제거용 안전장치
    private static final Pattern FOREIGN_CHAR_LEAK = Pattern.compile("[㐀-鿿぀-ヿẠ-ỿ]");

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

        // 프롬프트 조립 및 Gemini 호출 (호출 한도 초과 시 Groq로 대체)
        String prompt = buildPrompt(approvalRequest, quote, items, reasons);
        String summary;
        try {
            summary = geminiClient.generateContent(prompt);
        } catch (CustomException e) {
            if (e.getErrorCode() != ErrorCode.AI_SUMMARY_RATE_LIMITED) {
                throw e;
            }
            summary = groqClient.generateContent(prompt);
        }
        summary = FOREIGN_CHAR_LEAK.matcher(summary).replaceAll("");

        // 저장
        approvalRequest.updateAiRiskSummary(summary);
        approvalRequestRepository.save(approvalRequest);

        return AiRiskSummaryResponse.generated(approvalRequest.getId(), summary);
    }

    private String buildPrompt(ApprovalRequest approvalRequest, Quote quote, List<QuoteItem> items, List<QuoteApprovalReason> reasons) {
        StringBuilder sb = new StringBuilder();
        sb.append("당신은 B2B 영업 견적 리스크 분석 전문가입니다.\n");
        sb.append("아래 정보를 분석해서 영업관리자가 승인 여부를 빠르게 판단할 수 있도록 요약해 주세요.\n");
        sb.append("반드시 정확한 한국어로만 작성하고, 다른 언어(영어, 일본어, 베트남어 등)나 한자를 절대 섞지 마세요.\n\n");

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

        // 재요청 컨텍스트 — 승인 판단에서 가장 실용적인 정보이므로 별도 섹션으로 명시
        // 반려 후 재요청하면 rejectReason 필드 자체는 비워지므로, 이력(histories)에서 마지막 반려 사유를 찾아온다
        String lastRejectReason = findLastRejectReason(approvalRequest);

        sb.append("\n[요청 이력]\n");
        sb.append("- 요청 횟수: ").append(approvalRequest.getRequestCount()).append("회차")
                .append(approvalRequest.getRequestCount() > 1 ? " (재요청 건)\n" : " (최초 요청)\n");
        if (lastRejectReason != null) {
            sb.append("- 직전 반려 사유: ").append(lastRejectReason).append("\n");
        }
        if (approvalRequest.getRequestMemo() != null && !approvalRequest.getRequestMemo().isBlank()) {
            sb.append("- 영업사원 요청 사유: ").append(approvalRequest.getRequestMemo()).append("\n");
        }

        sb.append("\n[출력 형식 — 반드시 이 구조를 지켜서 작성하세요]\n");
        sb.append("1. 첫 줄: 이모지 하나 + 한 문장으로 핵심 판단 (예: \"⚠️ 할인율 초과 + 이익률 미달 — 신중 검토 필요\", \"✅ 이익률 양호, 승인 무리 없음\")\n");
        sb.append("2. \"핵심 리스크\" 섹션: 2~4개 bullet. 단순 수치 나열이 아니라 기준 대비 초과·미달 정도를 함께 명시할 것\n");
        if (lastRejectReason != null) {
            sb.append("   - 직전 반려 사유가 이번 요청에서 실제로 해소됐는지 반드시 언급할 것\n");
        }
        sb.append("3. \"체크포인트\" 섹션: 승인자가 결정 전에 확인하면 좋을 사항 1줄 (예: 장기계약 여부, 고객사 신용도, 재구매 가능성 등)\n");
        sb.append("불필요한 서론 없이 바로 위 형식대로만 출력하세요.\n");

        return sb.toString();
    }

    // rejectReason 필드는 재요청 시 초기화되므로, 이력에서 가장 최근 반려(REJECTED) 사유를 조회한다
    private String findLastRejectReason(ApprovalRequest approvalRequest) {
        if (approvalRequest.getRejectReason() != null && !approvalRequest.getRejectReason().isBlank()) {
            return approvalRequest.getRejectReason();
        }
        return approvalRequest.getHistories().stream()
                .filter(h -> h.getAction() == QuoteApprovalHistory.ActionType.REJECTED)
                .reduce((first, last) -> last)
                .map(QuoteApprovalHistory::getMemo)
                .filter(memo -> memo != null && !memo.isBlank())
                .orElse(null);
    }

    private ApprovalRequest findApprovalRequest(Long approvalRequestId) {
        return approvalRequestRepository.findByIdWithUsers(approvalRequestId)
                .orElseThrow(() -> new CustomException(ErrorCode.APPROVAL_REQUEST_NOT_FOUND));
    }
}
