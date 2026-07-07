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
import com.project.back.global.enums.ApprovalReasonType;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    // SUPER_ADMIN용: 캐시 무시하고 강제 재생성
    @Transactional
    public AiRiskSummaryResponse regenerateSummary(Long approvalRequestId) {
        ApprovalRequest approvalRequest = findApprovalRequest(approvalRequestId);
        return generateAndCache(approvalRequest);
    }

    // SALES_MANAGER용: 동일 부서만 허용
    @Transactional
    public AiRiskSummaryResponse getSummaryForManager(Long approvalRequestId, Long managerId) {
        ApprovalRequest approvalRequest = findApprovalRequestForManager(approvalRequestId, managerId);
        return generateOrGetCached(approvalRequest);
    }

    // SALES_MANAGER용: 동일 부서만 허용, 캐시 무시하고 강제 재생성
    @Transactional
    public AiRiskSummaryResponse regenerateSummaryForManager(Long approvalRequestId, Long managerId) {
        ApprovalRequest approvalRequest = findApprovalRequestForManager(approvalRequestId, managerId);
        return generateAndCache(approvalRequest);
    }

    private ApprovalRequest findApprovalRequestForManager(Long approvalRequestId, Long managerId) {
        ApprovalRequest approvalRequest = findApprovalRequest(approvalRequestId);

        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String managerDept = manager.getDepartment();
        String requesterDept = approvalRequest.getRequester().getDepartment();

        if (managerDept == null || !managerDept.equals(requesterDept)) {
            throw new CustomException(ErrorCode.APPROVAL_DEPT_MISMATCH);
        }

        return approvalRequest;
    }

    private AiRiskSummaryResponse generateOrGetCached(ApprovalRequest approvalRequest) {
        // 이미 생성된 요약이 있으면 캐시 반환
        if (approvalRequest.getAiRiskSummary() != null) {
            return AiRiskSummaryResponse.cached(
                    approvalRequest.getId(),
                    approvalRequest.getAiRiskSummary()
            );
        }
        return generateAndCache(approvalRequest);
    }

    // 캐시 여부와 무관하게 새로 생성해서 덮어쓴다 (수동 재생성 버튼용)
    private AiRiskSummaryResponse generateAndCache(ApprovalRequest approvalRequest) {
        String summary = generateSummary(approvalRequest);
        return AiRiskSummaryResponse.generated(approvalRequest.getId(), summary);
    }

    private String generateSummary(ApprovalRequest approvalRequest) {
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

        // 저장 (재생성 시 기존 캐시를 덮어씀)
        approvalRequest.updateAiRiskSummary(summary);
        approvalRequestRepository.save(approvalRequest);

        return summary;
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

        String excessDetail = buildExcessDetailSection(quote, items, reasons);
        if (!excessDetail.isBlank()) {
            sb.append("\n[정책 기준 대비 초과 내역 — 아래 수치를 그대로 인용해서 설명할 것]\n");
            sb.append(excessDetail);
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

        sb.append("\n[출력 형식 — 반드시 이 구조와 분량을 지켜서 작성하세요. 전체 답변은 400자를 넘기지 마세요]\n");
        sb.append("1. 첫 줄: 이모지 하나 + 한 문장(20자 내외)으로 핵심 판단만. 설명 붙이지 말 것 (예: \"⚠️ 할인율·이익률 리스크 — 신중 검토 필요\", \"✅ 이익률 양호, 승인 무리 없음\")\n");
        sb.append("2. \"핵심 리스크\" 섹션: 최대 2개 bullet, 각 bullet은 한 문장(35자 내외)으로 짧게. ")
                .append("\"할인율이 정책 기준을 초과했습니다\" 같은 단순 감지 문구 대신, ")
                .append("위 [정책 기준 대비 초과 내역]의 금액(원)·초과폭(%p)을 숫자만 압축해서 인용할 것. 배경 설명이나 부연은 생략할 것\n");
        if (lastRejectReason != null) {
            sb.append("   - 직전 반려 사유 해소 여부는 별도 bullet 없이 위 bullet 중 하나에 짧게 포함할 것\n");
        }
        sb.append("3. \"권장 조치\" 섹션: 최대 2개 bullet, 각 bullet은 한 문장(30자 내외)으로 실행 가능한 조치만 (예: \"할인율 10%로 재조정\", \"원가 재협상 후 재산정\"). 이유 설명 없이 조치만 쓸 것\n");
        sb.append("4. \"체크포인트\" 섹션: 1줄(20자 내외), 승인자가 확인할 사항 하나만\n");
        sb.append("각 섹션 제목만 쓰고 bullet은 \"- \"로 시작. 서론·결론·재강조 문장을 절대 추가하지 말고 위 4개 항목만 출력하세요.\n");

        return sb.toString();
    }

    // 정책 스냅샷 대비 실제 초과 금액·비율을 계산해 프롬프트에 구체적 근거로 제공한다.
    // ApprovalCheckService의 "가장 엄격한 기준" 판단 로직과 동일한 기준을 사용한다.
    private String buildExcessDetailSection(Quote quote, List<QuoteItem> items, List<QuoteApprovalReason> reasons) {
        if (reasons.isEmpty() || items.isEmpty()) {
            return "";
        }

        Set<ApprovalReasonType> reasonTypes = reasons.stream()
                .map(QuoteApprovalReason::getReasonType)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ApprovalReasonType.class)));

        StringBuilder sb = new StringBuilder();

        if (reasonTypes.contains(ApprovalReasonType.DISCOUNT_EXCEEDED)) {
            for (QuoteItem item : items) {
                BigDecimal maxRate = item.getEffectiveMaxDiscountRate();
                BigDecimal rate = item.getDiscountRate();
                if (maxRate == null || rate == null || rate.compareTo(maxRate) <= 0) {
                    continue;
                }
                BigDecimal excessRate = rate.subtract(maxRate);
                BigDecimal lineBase = item.getUnitPrice().multiply(item.getQuantity());
                BigDecimal excessAmount = lineBase.multiply(excessRate)
                        .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
                sb.append("- [할인율 초과] ").append(item.getProductName())
                        .append(": 정책 최대 ").append(pct(maxRate))
                        .append("% 대비 적용 ").append(pct(rate))
                        .append("% → ").append(pct(excessRate))
                        .append("%p 초과, 초과분 할인금액 약 ").append(won(excessAmount)).append("원\n");
            }
        }

        if (reasonTypes.contains(ApprovalReasonType.LOW_PROFIT)) {
            BigDecimal strictestMinProfit = items.stream()
                    .map(QuoteItem::getEffectiveMinProfitRate)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
            BigDecimal profitRate = quote.getProfitRate();
            if (strictestMinProfit != null && profitRate != null && profitRate.compareTo(strictestMinProfit) < 0) {
                BigDecimal gap = strictestMinProfit.subtract(profitRate);
                BigDecimal supplyAmount = quote.getSupplyAmount();
                BigDecimal shortfallAmount = supplyAmount != null
                        ? supplyAmount.multiply(gap).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                sb.append("- [이익률 미달] 정책 최소 ").append(pct(strictestMinProfit))
                        .append("% 대비 실제 ").append(pct(profitRate))
                        .append("% → ").append(pct(gap))
                        .append("%p 미달, 부족 이익 약 ").append(won(shortfallAmount)).append("원\n");
            }
        }

        if (reasonTypes.contains(ApprovalReasonType.HIGH_AMOUNT)) {
            BigDecimal strictestThreshold = items.stream()
                    .map(QuoteItem::getEffectiveApprovalThresholdAmount)
                    .filter(Objects::nonNull)
                    .min(Comparator.naturalOrder())
                    .orElse(null);
            BigDecimal totalAmount = quote.getTotalAmount();
            if (strictestThreshold != null && totalAmount != null
                    && strictestThreshold.compareTo(BigDecimal.ZERO) > 0
                    && totalAmount.compareTo(strictestThreshold) >= 0) {
                BigDecimal excessAmount = totalAmount.subtract(strictestThreshold);
                BigDecimal excessRate = excessAmount.multiply(BigDecimal.valueOf(100))
                        .divide(strictestThreshold, 1, RoundingMode.HALF_UP);
                sb.append("- [고액 견적] 승인 기준 ").append(won(strictestThreshold))
                        .append("원 대비 실제 ").append(won(totalAmount))
                        .append("원 → ").append(won(excessAmount))
                        .append("원 (").append(pct(excessRate)).append("%) 초과\n");
            }
        }

        return sb.toString();
    }

    private String won(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        return amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private String pct(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
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
