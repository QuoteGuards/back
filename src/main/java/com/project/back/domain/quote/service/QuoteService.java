package com.project.back.domain.quote.service;

import com.project.back.domain.customer.entity.Customer;
import com.project.back.domain.customer.repository.CustomerRepository;
import com.project.back.domain.discount.entity.DiscountPolicy;
import com.project.back.global.enums.ApprovalReasonType;
import com.project.back.global.enums.QuoteStatus;
import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.quote.entity.QuoteApprovalReason;
import com.project.back.domain.quote.entity.QuoteItem;
import com.project.back.domain.quote.repository.QuoteApprovalReasonRepository;
import com.project.back.domain.quote.repository.QuoteItemRepository;
import com.project.back.domain.quote.repository.QuoteRepository;
import com.project.back.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuoteService {

    private final QuoteRepository quoteRepository;
    private final QuoteItemRepository quoteItemRepository;
    private final QuoteApprovalReasonRepository approvalReasonRepository;
    private final CustomerRepository customerRepository;
    private final QuoteCalculationService calculationService;
    private final ApprovalCheckService approvalCheckService;

    // ── 견적 작성 (임시저장 / 작성완료) ─────────────────

    /**
     * 견적 신규 작성 (임시저장)
     * status = DRAFT, 금액 계산만 수행, 승인 판단 없음
     */
    @Transactional
    public Quote saveDraft(User createdBy,
                           Long customerId,
                           Long discountPolicyId,
                           String internalMemo,
                           LocalDate validUntil,
                           List<QuoteItemCommand> itemCommands) {

        Customer customer = getCustomerOrThrow(customerId);
        DiscountPolicy policy = resolveDiscountPolicy(discountPolicyId);

        Quote quote = Quote.builder()
                .createdBy(createdBy)
                .customer(customer)
                .discountPolicy(policy)
                .quoteNumber(generateQuoteNumber())
                .internalMemo(internalMemo)
                .validUntil(validUntil)
                .status(QuoteStatus.DRAFT)
                .build();

        quoteRepository.save(quote);

        List<QuoteItem> items = buildItems(quote, itemCommands);
        quoteItemRepository.saveAll(items);

        // 금액 + 내부분석 자동 계산
        calculationService.calculate(quote, items);

        return quote;
    }

    /**
     * 견적 작성완료 제출
     * 승인 필요 여부 판단 → ApprovalReasonType 저장 → 상태 전이
     */
    @Transactional
    public Quote submitQuote(Long quoteId, User requester) {
        Quote quote = getQuoteWithDetailsOrThrow(quoteId);
        validateOwner(quote, requester);

        List<QuoteItem> items = quoteItemRepository.findByQuoteIdOrderBySortOrderAsc(quoteId);

        // 승인 필요 여부 판단
        List<ApprovalReasonType> reasons = approvalCheckService.check(
                quote.getDiscountPolicy(),
                items,
                quote.getTotalAmount(),
                quote.getProfitRate()
        );

        boolean approvalRequired = !reasons.isEmpty();

        // 기존 사유 초기화 후 새로 저장 (재제출 가능성 고려)
        approvalReasonRepository.deleteByQuoteId(quoteId);
        if (approvalRequired) {
            saveApprovalReasons(quote, reasons);
        }

        quote.complete(approvalRequired);
        return quote;
    }

    //견적 수정(DRAFT / REVISING 상태에서만 가능)
    @Transactional
    public Quote updateQuote(Long quoteId,
                             User requester,
                             Long customerId,
                             String internalMemo,
                             LocalDate validUntil,
                             List<QuoteItemCommand> itemCommands) {

        Quote quote = getQuoteWithDetailsOrThrow(quoteId);
        validateOwner(quote, requester);
        validateEditable(quote);

        Customer customer = getCustomerOrThrow(customerId);
        quote.updateInfo(customer, internalMemo, validUntil);

        // 기존 항목 삭제 후 재작성
        quoteItemRepository.deleteByQuoteId(quoteId);
        List<QuoteItem> items = buildItems(quote, itemCommands);
        quoteItemRepository.saveAll(items);

        calculationService.calculate(quote, items);

        return quote;
    }

    //내 견적 목록 ( 다중 조건 검색)
    public List<Quote> searchMyQuotes(Long userId,
                                      QuoteStatus status,
                                      String customerName,
                                      String quoteNumber,
                                      LocalDateTime from,
                                      LocalDateTime to) {
        return quoteRepository.searchMyQuotes(userId, status, customerName, quoteNumber, from, to);
    }

    // 견적 상세 조회
    public Quote getQuoteDetail(Long quoteId, User requester) {
        Quote quote = getQuoteWithDetailsOrThrow(quoteId);
        validateOwner(quote, requester);
        return quote;
    }

    //내부 검토 조회 — 이익률/원가 등 민감 정보 포함
    public Quote getInternalAnalysis(Long quoteId, User requester) {
        Quote quote = getQuoteWithDetailsOrThrow(quoteId);
        validateOwner(quote, requester);
        return quote;
    }

    //최근 견적 재사용(동일 내용으로 새 견적번호의 신규 견적(DRAFT) 생성)
    @Transactional
    public Quote reuseQuote(Long sourceQuoteId, User requester) {
        Quote source = getQuoteWithDetailsOrThrow(sourceQuoteId);
        validateOwner(source, requester);

        Quote newQuote = Quote.builder()
                .createdBy(requester)
                .customer(source.getCustomer())
                .discountPolicy(source.getDiscountPolicy())
                .quoteNumber(generateQuoteNumber())
                .internalMemo(source.getInternalMemo())
                .validUntil(source.getValidUntil())
                .status(QuoteStatus.DRAFT)
                .build();

        quoteRepository.save(newQuote);

        // 원본 항목 복사
        List<QuoteItem> sourceItems = quoteItemRepository.findByQuoteIdOrderBySortOrderAsc(sourceQuoteId);
        List<QuoteItem> copiedItems = copyItems(newQuote, sourceItems);
        quoteItemRepository.saveAll(copiedItems);

        calculationService.calculate(newQuote, copiedItems);

        return newQuote;
    }

    //만료 견적 재작성
    @Transactional
    public Quote rewriteExpiredQuote(Long expiredQuoteId, User requester) {
        Quote expired = getQuoteWithDetailsOrThrow(expiredQuoteId);
        validateOwner(expired, requester);

        if (expired.getStatus() != QuoteStatus.EXPIRED) {
            throw new IllegalStateException("만료된 견적만 재작성할 수 있습니다.");
        }

        Long originalId = expired.getOriginalQuote() != null
                ? expired.getOriginalQuote().getId()
                : expired.getId();

        int nextVersion = quoteRepository.findByOriginalQuoteIdOrderByVersionNoAsc(originalId)
                .stream()
                .mapToInt(Quote::getVersionNo)
                .max()
                .orElse(expired.getVersionNo()) + 1;

        expired.markAsNotLatest();

        Quote.QuoteBuilder builder = Quote.builder()
                .createdBy(requester)
                .customer(expired.getCustomer())
                .discountPolicy(expired.getDiscountPolicy())
                .quoteNumber(generateQuoteNumber())
                .internalMemo(expired.getInternalMemo())
                .validUntil(LocalDate.now().plusMonths(1))
                .status(QuoteStatus.DRAFT)
                .versionNo(nextVersion)
                .isLatest(true);

        Quote originalQuote = (expired.getOriginalQuote() != null)
                ? expired.getOriginalQuote()
                : expired;
        builder.originalQuote(originalQuote);

        Quote newQuote = builder.build();
        quoteRepository.save(newQuote);

        // 항목 복사
        List<QuoteItem> sourceItems = quoteItemRepository.findByQuoteIdOrderBySortOrderAsc(expiredQuoteId);
        List<QuoteItem> copiedItems = copyItems(newQuote, sourceItems);
        quoteItemRepository.saveAll(copiedItems);

        calculationService.calculate(newQuote, copiedItems);

        return newQuote;
    }

    //만료 일괄 처리 (스케줄러)
    // 매일 자정 유효기간 지난 견적 일괄 만료 처리(APPROVAL_NOT_REQUIRED, APPROVED 상태 견적 중 validUntil)
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void expireOverdueQuotes() {
        List<Quote> targets = quoteRepository.findExpiredQuotes(
                Arrays.asList(QuoteStatus.APPROVAL_NOT_REQUIRED, QuoteStatus.APPROVED));
        targets.forEach(Quote::expire);
    }

    private Customer getCustomerOrThrow(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 고객입니다. id=" + customerId));
    }

    private Quote getQuoteWithDetailsOrThrow(Long quoteId) {
        return quoteRepository.findByIdWithDetails(quoteId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 견적입니다. id=" + quoteId));
    }

    // 작성자 본인 여부 검증
    private void validateOwner(Quote quote, User requester) {
        if (!quote.getCreatedBy().getId().equals(requester.getId())) {
            throw new IllegalStateException("본인이 작성한 견적만 접근할 수 있습니다.");
        }
    }

    // 수정 가능 상태 검증 (DRAFT or REVISING)
    private void validateEditable(Quote quote) {
        if (quote.getStatus() != QuoteStatus.DRAFT && quote.getStatus() != QuoteStatus.REVISING) {
            throw new IllegalStateException("수정 가능한 상태가 아닙니다. 현재 상태: " + quote.getStatus());
        }
    }

    // 견적번호 생성 : Q + yyyyMMdd + 6자리 랜덤 (ex. Q202406180001)
    private String generateQuoteNumber() {
        String base = "Q" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String suffix = String.format("%06d", (long) (Math.random() * 1_000_000));
        String candidate = base + suffix;
        // 중복 시 재시도
        while (quoteRepository.existsByQuoteNumber(candidate)) {
            suffix = String.format("%06d", (long) (Math.random() * 1_000_000));
            candidate = base + suffix;
        }
        return candidate;
    }

    // DiscountPolicy 조회 (null 허용 - 정책 미지정 견적)
    private DiscountPolicy resolveDiscountPolicy(Long policyId) {
        // Todo:현재는 null 반환 -> DiscountPolicy 레포지토리 구현되면 변경
        return null;
    }

    // 커맨드 → QuoteItem 변환
    private List<QuoteItem> buildItems(Quote quote, List<QuoteItemCommand> commands) {
        int[] order = {0};
        return commands.stream()
                .map(cmd -> QuoteItem.builder()
                        .quote(quote)
                        .productId(cmd.productId())
                        .productName(cmd.productName())
                        .productCode(cmd.productCode())
                        .unitPrice(cmd.unitPrice())
                        .costPrice(cmd.costPrice() != null ? cmd.costPrice() : BigDecimal.ZERO)
                        .quantity(cmd.quantity())
                        .discountRate(cmd.discountRate() != null ? cmd.discountRate() : BigDecimal.ZERO)
                        .vatApplicable(cmd.vatApplicable() != null ? cmd.vatApplicable() : true)
                        .sortOrder(order[0]++)
                        .build())
                .toList();
    }

    // 항목 복사 (재사용/재작성용)
    private List<QuoteItem> copyItems(Quote newQuote, List<QuoteItem> sourceItems) {
        int[] order = {0};
        return sourceItems.stream()
                .map(src -> QuoteItem.builder()
                        .quote(newQuote)
                        .productId(src.getProductId())
                        .productName(src.getProductName())
                        .productCode(src.getProductCode())
                        .unitPrice(src.getUnitPrice())
                        .costPrice(src.getCostPrice())
                        .quantity(src.getQuantity())
                        .discountRate(src.getDiscountRate())
                        .vatApplicable(src.getVatApplicable())
                        .sortOrder(order[0]++)
                        .build())
                .toList();
    }

    // 승인 사유 저장
    private void saveApprovalReasons(Quote quote, List<ApprovalReasonType> reasons) {
        List<QuoteApprovalReason> entities = reasons.stream()
                .map(reason -> QuoteApprovalReason.builder()
                        .quote(quote)
                        .reasonType(reason)
                        .reasonMessage(defaultMessage(reason, quote))
                        .build())
                .toList();
        approvalReasonRepository.saveAll(entities);
    }

    private String defaultMessage(ApprovalReasonType reason, Quote quote) {
        return switch (reason) {
            case DISCOUNT_EXCEEDED -> "적용 할인율이 정책 허용치를 초과합니다.";
            case LOW_PROFIT -> String.format("이익률 %.2f%%가 최소 기준 미만입니다.", quote.getProfitRate());
            case HIGH_AMOUNT -> String.format("견적 총금액 %,.0f원이 승인 기준금액을 초과합니다.",
                    quote.getTotalAmount().doubleValue());
        };
    }

     //견적 항목 입력 커맨드
    public record QuoteItemCommand(
            Long productId,
            String productName,
            String productCode,
            BigDecimal unitPrice,
            BigDecimal costPrice,
            BigDecimal quantity,
            BigDecimal discountRate,
            Boolean vatApplicable
    ) {}
}
