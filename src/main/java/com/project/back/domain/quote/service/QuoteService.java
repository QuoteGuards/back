package com.project.back.domain.quote.service;

import com.project.back.domain.customer.entity.Customer;
import com.project.back.domain.customer.repository.CustomerRepository;
import com.project.back.domain.discount.entity.DiscountPolicy;
// 💡 TODO: [2번 팀원 리포지토리 완료 시 변경할 곳] 아래 임시 레포지토리 패키지 경로를 팀원의 실제 경로로 맞추거나 주석을 해제하세요.
// import com.project.back.domain.discount.repository.DiscountPolicyRepository;
import com.project.back.domain.discount.repository.DiscountPolicyRepository;
import com.project.back.domain.product.repository.ProductRepository;
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
import jakarta.validation.constraints.*;
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
    private final DiscountPolicyRepository discountPolicyRepository;
    private final ProductRepository productRepository;

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
        Customer customer = getCustomerOrThrow(customerId, createdBy.getId());
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

        //편집 가능한 상태(DRAFT, REVISING)인지 검증
        validateEditable(quote);

        List<QuoteItem> items = quoteItemRepository.findByQuoteIdOrderBySortOrderAsc(quoteId);
        List<ApprovalReasonType> reasons = approvalCheckService.check(
                quote.getDiscountPolicy(), items, quote.getTotalAmount(), quote.getProfitRate());

        boolean approvalRequired = !reasons.isEmpty();
        approvalReasonRepository.deleteByQuote_Id(quoteId);
        if (approvalRequired) {
            List<QuoteApprovalReason> reasonEntities = reasons.stream()
                    .map(r -> QuoteApprovalReason.of(
                            quote,
                            QuoteApprovalReason.ReasonType.valueOf(r.name()),
                            r.name()))
                    .toList();
            approvalReasonRepository.saveAll(reasonEntities);
        }

        quote.complete(approvalRequired);

        // 견적 제출 시 통계 갱신 - 커밋 이후 재집계
        userStatsUpdateService.recalculateAfterCommit(requester.getId());

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

        Customer customer = getCustomerOrThrow(customerId, requester.getId());

        //고객 정보가 바뀌었을 때를 대비해 스냅샷을 새로 생성
        QuoteCustomer snapshot = QuoteCustomer.builder()
                .companyName(customer.getCompanyName())
                .contactName(customer.getContactName())
                .email(customer.getEmail())
                .phone(customer.getPhone())
                .address(customer.getAddress())
                .build();

        quote.updateInfo(customer, snapshot, internalMemo, issuedDate, validUntil, deliveryTerm);

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
                .quoteCustomer(source.getQuoteCustomer())
                .company(source.getCompany())
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
                .quoteCustomer(expired.getQuoteCustomer())
                .company(expired.getCompany())
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

    private Customer getCustomerOrThrow(Long customerId, Long requesterId) {
        return customerRepository.findByIdAndUserId(customerId, requesterId)
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

    private DiscountPolicy resolveDiscountPolicy(Long policyId) {
        if (policyId == null) {
            return null;
        }
        return discountPolicyRepository.findById(policyId)
                .orElseThrow(() -> new CustomException(ErrorCode.DISCOUNT_POLICY_NOT_FOUND));
    }

    private List<QuoteItem> buildItems(Quote quote, List<QuoteItemCommand> commands) {
        int[] order = {0};
        return commands.stream()
                .map(cmd -> {
                    cmd.validateDiscountReason();
                    var product = productRepository.findById(cmd.productId())
                            .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
                    if (!product.isActive()) throw new CustomException(ErrorCode.PRODUCT_NOT_FOUND);

                    QuoteItem item = QuoteItem.builder()
                            .quote(quote)
                            .productId(product.getId())
                            .productName(product.getName())
                            .productCode(product.getCode())
                            .spec(product.getSpec())
                            .unitPrice(product.getUnitPrice())
                            .costPrice(product.getCostPrice())
                            .quantity(cmd.quantity())
                            .discountRate(cmd.discountRate() != null ? cmd.discountRate() : BigDecimal.ZERO)
                            .discountReason(cmd.discountReason())
                            .vatApplicable(cmd.vatApplicable() != null ? cmd.vatApplicable() : true)
                            .sortOrder(order[0]++)
                            .build();

                    quote.addItem(item);
                    return item;
                })
                .toList();
    }

    private List<QuoteItem> copyItems(Quote newQuote, List<QuoteItem> sourceItems) {
        int[] order = {0};
        return sourceItems.stream()
                .map(src -> {
                    QuoteItem item = QuoteItem.builder()
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
                            .build();

                    newQuote.addItem(item);
                    return item;
                })
                .toList();
    }

    public record QuoteItemCommand(
            Long productId,
            @NotBlank(message = "제품명은 필수입니다.") String productName,
            String productCode,
            @Size(max = 200) String spec,
            @NotNull(message = "단가는 필수입니다.") @DecimalMin("0") BigDecimal unitPrice,
            @NotNull(message = "원가는 필수입니다.") @DecimalMin("0") BigDecimal costPrice,
            @NotNull(message = "수량은 필수입니다.") @DecimalMin("0.01") BigDecimal quantity,
            @DecimalMin("0") @DecimalMax("100") BigDecimal discountRate,
            @NotNull(message = "VAT 적용 여부는 필수입니다.") Boolean vatApplicable,
            String discountReason // 할인 사유 필드
    ) {
        public void validateDiscountReason() {
            // 할인율이 0보다 큰데 사유가 없으면 에러
            if (discountRate != null && discountRate.compareTo(BigDecimal.ZERO) > 0) {
                if (discountReason == null || discountReason.isBlank()) {
                    throw new CustomException(ErrorCode.DISCOUNT_REASON_REQUIRED);
                }
            }
        }
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}