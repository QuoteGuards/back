package com.project.back.domain.quote.service;

import com.project.back.domain.customer.entity.Customer;
import com.project.back.domain.customer.repository.CustomerRepository;
import com.project.back.domain.discount.entity.DiscountPolicy;
// 💡 TODO: [2번 팀원 리포지토리 완료 시 변경할 곳] 아래 임시 레포지토리 패키지 경로를 팀원의 실제 경로로 맞추거나 주석을 해제하세요.
// import com.project.back.domain.discount.repository.DiscountPolicyRepository;
import com.project.back.domain.quote.dto.response.QuoteDetailResponse;
import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.approval.entity.QuoteApprovalReason;
import com.project.back.domain.quote.entity.QuoteItem;
import com.project.back.domain.quote.entity.QuoteCustomer;
import com.project.back.domain.quote.entity.QuoteCompany;
import com.project.back.domain.approval.repository.QuoteApprovalReasonRepository;
import com.project.back.domain.quote.repository.QuoteItemRepository;
import com.project.back.domain.quote.repository.QuoteRepository;
import com.project.back.domain.training.service.TrainingService;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.domain.user.service.UserStatsUpdateService;
import com.project.back.global.enums.ApprovalReasonType;
import com.project.back.global.enums.QuoteStatus;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
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

    private final UserRepository userRepository;
    private final QuoteRepository quoteRepository;
    private final QuoteItemRepository quoteItemRepository;
    private final QuoteApprovalReasonRepository approvalReasonRepository;
    private final CustomerRepository customerRepository;
    private final QuoteCalculationService calculationService;
    private final ApprovalCheckService approvalCheckService;
    private final TrainingService trainingService;
    private final UserStatsUpdateService userStatsUpdateService;

    // 💡 TODO: [제품 팀원 리포지토리 완료 시 변경할 곳] 2번 팀원이 DiscountPolicyRepository를 주입할 수 있게 선언해주면 주석을 해제
    // private final DiscountPolicyRepository discountPolicyRepository;

    @Transactional
    public Quote saveDraft(User createdBy,
                           Long customerId,
                           Long discountPolicyId,
                           String internalMemo,
                           LocalDate issuedDate,
                           LocalDate validUntil,
                           String deliveryTerm,
                           List<QuoteItemCommand> itemCommands) {

        validateTrainingCompleted(createdBy.getId());
        Customer customer = getCustomerOrThrow(customerId);
        DiscountPolicy policy = resolveDiscountPolicy(discountPolicyId);

        //원본 고객 엔티티 데이터 복사 후 발행 시점 박제용 스냅샷 생성
        QuoteCustomer customerSnapshot = QuoteCustomer.builder()
                .companyName(customer.getCompanyName())
                .contactName(customer.getContactName())
                .email(customer.getEmail())
                .phone(customer.getPhone())
                .address(customer.getAddress())
                .build();

        //자사 정보 고정 스냅샷 생성
        QuoteCompany companySnapshot = QuoteCompany.builder()
                .name("QuoteGuard 주식회사")
                .businessNumber("123-45-67890")
                .email("sales-support@quoteguard.com")
                .phone("02-555-1234")
                .address("서울특별시 소프트구 21길 4년제빌딩 3층")
                .build();

        Quote quote = Quote.builder()
                .createdBy(createdBy)
                .customer(customer)
                .discountPolicy(policy)
                .quoteNumber(generateQuoteNumber())
                .internalMemo(internalMemo)
                .issuedDate(issuedDate)
                .validUntil(validUntil)
                .deliveryTerm(deliveryTerm)
                .status(QuoteStatus.DRAFT)
                .quoteCustomer(customerSnapshot)
                .company(companySnapshot)
                .build();

        quoteRepository.save(quote);

        List<QuoteItem> items = buildItems(quote, itemCommands);
        quoteItemRepository.saveAll(items);
        calculationService.calculate(quote, items);

        return quote;
    }

    @Transactional
    public Quote submitQuote(Long quoteId, User requester) {
        validateTrainingCompleted(requester.getId());
        Quote quote = getQuoteWithDetailsOrThrow(quoteId);
        validateOwner(quote, requester);

        List<QuoteItem> items = quoteItemRepository.findByQuoteIdOrderBySortOrderAsc(quoteId);
        List<ApprovalReasonType> reasons = approvalCheckService.check(
                quote.getDiscountPolicy(), items, quote.getTotalAmount(), quote.getProfitRate());

        boolean approvalRequired = !reasons.isEmpty();
        approvalReasonRepository.deleteByQuoteId(quoteId);
        if (approvalRequired) saveApprovalReasons(quote, reasons);

        quote.complete(approvalRequired);

        // 견적 제출 시 통계 갱신
        userStatsUpdateService.recalculate(requester.getId());

        return quote;
    }

    @Transactional
    public Quote updateQuote(Long quoteId, User requester, Long customerId,
                             String internalMemo,
                             LocalDate issuedDate,
                             LocalDate validUntil,
                             String deliveryTerm,
                             List<QuoteItemCommand> itemCommands) {

        validateTrainingCompleted(requester.getId());
        Quote quote = getQuoteWithDetailsOrThrow(quoteId);
        validateOwner(quote, requester);
        validateEditable(quote);

        Customer customer = getCustomerOrThrow(customerId);
        quote.updateInfo(customer, internalMemo, issuedDate, validUntil, deliveryTerm);

        quoteItemRepository.deleteByQuoteId(quoteId);
        List<QuoteItem> items = buildItems(quote, itemCommands);
        quoteItemRepository.saveAll(items);
        calculationService.calculate(quote, items);

        return quote;
    }

    public List<Quote> searchMyQuotes(Long userId, QuoteStatus status, String customerName,
                                      String quoteNumber, LocalDateTime from, LocalDateTime to) {
        return quoteRepository.searchMyQuotes(userId, status, customerName, quoteNumber, from, to);
    }

    public Quote getQuoteDetail(Long quoteId, User requester) {
        Quote quote = getQuoteWithDetailsOrThrow(quoteId);
        validateOwner(quote, requester);
        return quote;
    }

    public Quote getInternalAnalysis(Long quoteId, User requester) {
        Quote quote = getQuoteWithDetailsOrThrow(quoteId);
        validateOwner(quote, requester);
        return quote;
    }

    @Transactional
    public Quote reuseQuote(Long sourceQuoteId, User requester) {
        validateTrainingCompleted(requester.getId());
        Quote source = getQuoteWithDetailsOrThrow(sourceQuoteId);
        validateOwner(source, requester);

        Quote newQuote = Quote.builder()
                .createdBy(requester)
                .customer(source.getCustomer())
                .discountPolicy(source.getDiscountPolicy())
                .quoteNumber(generateQuoteNumber())
                .internalMemo(source.getInternalMemo())
                .issuedDate(LocalDate.now())
                .validUntil(source.getValidUntil())
                .deliveryTerm(source.getDeliveryTerm())
                .status(QuoteStatus.DRAFT)
                .quoteCustomer(source.getQuoteCustomer()) // 삼룡님 변수명 싱크 완료
                .company(source.getCompany())             // 삼룡님 변수명 싱크 완료
                .build();

        quoteRepository.save(newQuote);
        List<QuoteItem> copiedItems = copyItems(newQuote,
                quoteItemRepository.findByQuoteIdOrderBySortOrderAsc(sourceQuoteId));
        quoteItemRepository.saveAll(copiedItems);
        calculationService.calculate(newQuote, copiedItems);

        return newQuote;
    }

    @Transactional
    public Quote rewriteExpiredQuote(Long expiredQuoteId, User requester) {
        validateTrainingCompleted(requester.getId());
        Quote expired = getQuoteWithDetailsOrThrow(expiredQuoteId);
        validateOwner(expired, requester);

        if (expired.getStatus() != QuoteStatus.EXPIRED)
            throw new CustomException(ErrorCode.QUOTE_NOT_EXPIRED);

        Long originalId = expired.getOriginalQuote() != null
                ? expired.getOriginalQuote().getId() : expired.getId();

        int nextVersion = quoteRepository.findByOriginalQuoteIdOrderByVersionNoAsc(originalId)
                .stream().mapToInt(Quote::getVersionNo).max().orElse(expired.getVersionNo()) + 1;

        expired.markAsNotLatest();

        Quote originalQuote = expired.getOriginalQuote() != null ? expired.getOriginalQuote() : expired;

        Quote newQuote = Quote.builder()
                .createdBy(requester)
                .customer(expired.getCustomer())
                .discountPolicy(expired.getDiscountPolicy())
                .quoteNumber(generateQuoteNumber())
                .internalMemo(expired.getInternalMemo())
                .issuedDate(LocalDate.now())
                .validUntil(LocalDate.now().plusMonths(1))
                .deliveryTerm(expired.getDeliveryTerm())
                .status(QuoteStatus.DRAFT)
                .versionNo(nextVersion)
                .isLatest(true)
                .originalQuote(originalQuote)
                .quoteCustomer(expired.getQuoteCustomer()) // 삼룡님 변수명 싱크 완료
                .company(expired.getCompany())             // 삼룡님 변수명 싱크 완료
                .build();

        quoteRepository.save(newQuote);
        List<QuoteItem> copiedItems = copyItems(newQuote,
                quoteItemRepository.findByQuoteIdOrderBySortOrderAsc(expiredQuoteId));
        quoteItemRepository.saveAll(copiedItems);
        calculationService.calculate(newQuote, copiedItems);

        return newQuote;
    }

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void expireOverdueQuotes() {
        quoteRepository.findExpiredQuotes(
                        Arrays.asList(QuoteStatus.APPROVAL_NOT_REQUIRED, QuoteStatus.APPROVED))
                .forEach(Quote::expire);
    }

    public QuoteDetailResponse getQuote(String quoteNumber, Long userId) {
        User user = findUser(userId);
        Quote quote = quoteRepository.findByQuoteNumberAndCreatedBy(quoteNumber, user)
                .orElseThrow(() -> new CustomException(ErrorCode.QUOTE_NOT_FOUND));
        return QuoteDetailResponse.from(quote);
    }

    private Customer getCustomerOrThrow(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomException(ErrorCode.CUSTOMER_NOT_FOUND));
    }

    private Quote getQuoteWithDetailsOrThrow(Long quoteId) {
        Quote quote = quoteRepository.findByIdWithDetails(quoteId)
                .orElseThrow(() -> new CustomException(ErrorCode.QUOTE_NOT_FOUND));
        quoteRepository.findByIdWithApprovalReasons(quoteId);
        return quote;
    }

    private void validateOwner(Quote quote, User requester) {
        if (!quote.getCreatedBy().getId().equals(requester.getId()))
            throw new CustomException(ErrorCode.QUOTE_ACCESS_DENIED);
    }

    private void validateEditable(Quote quote) {
        if (quote.getStatus() != QuoteStatus.DRAFT && quote.getStatus() != QuoteStatus.REVISING)
            throw new CustomException(ErrorCode.QUOTE_NOT_EDITABLE);
    }

    private void validateTrainingCompleted(Long userId) {
        if (!trainingService.isTrainingCompleted(userId)) {
            throw new CustomException(ErrorCode.TRAINING_NOT_COMPLETED);
        }
    }

    private String generateQuoteNumber() {
        String base = "Q" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String suffix = String.format("%06d", (long) (Math.random() * 1_000_000));
        String candidate = base + suffix;
        while (quoteRepository.existsByQuoteNumber(candidate)) {
            suffix = String.format("%06d", (long) (Math.random() * 1_000_000));
            candidate = base + suffix;
        }
        return candidate;
    }

    /**
     * 💡 문제 ③ 해결 파트
     */
    private DiscountPolicy resolveDiscountPolicy(Long policyId) {
        // 💡 TODO: [2번 팀원 리포지토리 완료 시 변경할 곳]
        // 2번 팀원이 DiscountPolicyRepository를 만들고 프로젝트를 합치면, 아래 'return null;' 주석을 해제하고 실제 DB 조회 코드를 활성화하세요.
        /*
        Long targetPolicyId = (policyId != null) ? policyId : 1L;
        return discountPolicyRepository.findById(targetPolicyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 할인 정책입니다."));
        */
        return null;
    }

    private List<QuoteItem> buildItems(Quote quote, List<QuoteItemCommand> commands) {
        int[] order = {0};
        return commands.stream()
                .map(cmd -> QuoteItem.builder()
                        .quote(quote)
                        .productId(cmd.productId())
                        .productName(cmd.productName())
                        .productCode(cmd.productCode())
                        .spec(cmd.spec())
                        .unitPrice(cmd.unitPrice())
                        .costPrice(cmd.costPrice() != null ? cmd.costPrice() : BigDecimal.ZERO)
                        .quantity(cmd.quantity())
                        .discountRate(cmd.discountRate() != null ? cmd.discountRate() : BigDecimal.ZERO)
                        .vatApplicable(cmd.vatApplicable() != null ? cmd.vatApplicable() : true)
                        .sortOrder(order[0]++)
                        .build())
                .toList();
    }

    private List<QuoteItem> copyItems(Quote newQuote, List<QuoteItem> sourceItems) {
        int[] order = {0};
        return sourceItems.stream()
                .map(src -> QuoteItem.builder()
                        .quote(newQuote)
                        .productId(src.getProductId())
                        .productName(src.getProductName())
                        .productCode(src.getProductCode())
                        .spec(src.getSpec())
                        .unitPrice(src.getUnitPrice())
                        .costPrice(src.getCostPrice())
                        .quantity(src.getQuantity())
                        .discountRate(src.getDiscountRate())
                        .vatApplicable(src.getVatApplicable())
                        .sortOrder(order[0]++)
                        .build())
                .toList();
    }

    private void saveApprovalReasons(Quote quote, List<ApprovalReasonType> reasons) {
        approvalReasonRepository.saveAll(reasons.stream()
                .map(reason -> QuoteApprovalReason.builder()
                        .quote(quote)
                        .reasonType(QuoteApprovalReason.ReasonType.valueOf(reason.name()))
                        .reasonMessage(defaultMessage(reason, quote))
                        .build())
                .toList());
    }

    private String defaultMessage(ApprovalReasonType reason, Quote quote) {
        return switch (reason) {
            case DISCOUNT_EXCEEDED -> "적용 할인율이 정책 허용치를 초과합니다.";
            case LOW_PROFIT -> String.format("이익률 %.2f%%가 최소 기준 미만입니다.", quote.getProfitRate());
            case HIGH_AMOUNT -> String.format("견적 총금액 %,.0f원이 승인 기준금액을 초과합니다.",
                    quote.getTotalAmount().doubleValue());
        };
    }

    public record QuoteItemCommand(
            Long productId, String productName, String productCode,
            String spec,
            BigDecimal unitPrice, BigDecimal costPrice,
            BigDecimal quantity, BigDecimal discountRate, Boolean vatApplicable
    ) {}

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}