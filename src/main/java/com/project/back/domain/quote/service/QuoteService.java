package com.project.back.domain.quote.service;

import com.project.back.domain.customer.entity.Customer;
import com.project.back.domain.customer.repository.CustomerRepository;
import com.project.back.domain.discount.entity.DiscountPolicy;
import com.project.back.domain.discount.repository.DiscountPolicyRepository;
import com.project.back.domain.product.entity.Product;
import com.project.back.domain.product.repository.ProductRepository;
import com.project.back.domain.quote.dto.response.AdminQuoteListResponse;
import com.project.back.domain.quote.dto.response.QuoteDetailResponse;
import com.project.back.domain.quote.dto.response.QuoteProductContextResponse;
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
import com.project.back.domain.user.entity.UserRole;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.domain.user.service.UserStatsUpdateService;
import com.project.back.global.enums.ApprovalReasonType;
import com.project.back.global.enums.QuoteStatus;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
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
    private final com.project.back.notification.service.NotificationService notificationService;

    @Transactional
    public Quote saveDraft(User createdBy,
                           Long customerId,
                           Long discountPolicyId,
                           String internalMemo,
                           LocalDate issuedDate,
                           LocalDate validUntil,
                           String deliveryTerm,
                           List<QuoteItemCommand> itemCommands) {

        validateQuoteWriterRole(createdBy);
        validateTrainingCompleted(createdBy);
        validateQuoteDates(issuedDate, validUntil);
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

        List<QuoteItem> items = buildItems(quote, itemCommands, policy, null);
        quoteItemRepository.saveAll(items);
        calculationService.calculate(quote, items);

        return quote;
    }

    @Transactional
    public Quote submitQuote(Long quoteId, User requester) {
        validateQuoteWriterRole(requester);
        validateTrainingCompleted(requester);
        Quote quote = getQuoteWithDetailsOrThrow(quoteId);
        validateOwner(quote, requester);
        validateEditable(quote);
        validateQuoteDates(quote.getIssuedDate(), quote.getValidUntil());

        List<QuoteItem> items = quoteItemRepository.findByQuoteIdOrderBySortOrderAsc(quoteId);
        List<ApprovalReasonType> reasons = approvalCheckService.check(
                quote.getDiscountPolicy(), items, quote.getTotalAmount(), quote.getProfitRate());

        boolean approvalRequired = !reasons.isEmpty();

        // 이전 승인/반려 사유 초기화 (orphanRemoval — bulk delete와 중복 호출 금지)
        quote.getApprovalReasons().clear();

        if (approvalRequired) {
            List<QuoteApprovalReason> reasonEntities = reasons.stream()
                    .map(r -> QuoteApprovalReason.of(quote, r, r.getDefaultMessage()))
                    .toList();
            approvalReasonRepository.saveAll(reasonEntities);
        }

        quote.complete(approvalRequired);
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

        validateQuoteWriterRole(requester);
        validateTrainingCompleted(requester);
        validateQuoteDates(issuedDate, validUntil);
        Quote quote = getQuoteWithDetailsOrThrow(quoteId);
        validateOwner(quote, requester);
        validateEditable(quote);

        // 이전 반려/승인 사유 초기화
        quote.getApprovalReasons().clear();

        Customer customer = getCustomerOrThrow(customerId, requester.getId());

        QuoteCustomer snapshot = QuoteCustomer.builder()
                .companyName(customer.getCompanyName())
                .contactName(customer.getContactName())
                .email(customer.getEmail())
                .phone(customer.getPhone())
                .address(customer.getAddress())
                .build();

        quote.updateInfo(customer, snapshot, internalMemo, issuedDate, validUntil, deliveryTerm);

        Map<Long, QuoteItem> existingByProductId = quote.getItems().stream()
                .filter(item -> item.getProductId() != null)
                .collect(Collectors.toMap(QuoteItem::getProductId, Function.identity(), (a, b) -> a));

        List<QuoteItem> newItems = buildItems(quote, itemCommands, quote.getDiscountPolicy(), existingByProductId);

        // 엔티티 내부에서 클리어하고 새로 추가
        quote.replaceItems(newItems);

        // 상태를 REVISING으로 명시하여 수정 중임을 확실히 함
        quote.startRevising();

        calculationService.calculate(quote, newItems);

        return quote;
    }

    public List<Quote> searchMyQuotes(Long userId, QuoteStatus status, String customerName,
                                      String quoteNumber, LocalDateTime from, LocalDateTime to) {
        return quoteRepository.searchMyQuotes(userId, status, customerName, quoteNumber, from, to);
    }

    // 전체 견적 목록(SUPER_ADMIN 전용)
    public List<AdminQuoteListResponse> searchAdminQuotes(User requester,
                                                          QuoteStatus status,
                                                          String customerName,
                                                          String quoteNumber,
                                                          String writerName,
                                                          LocalDateTime from,
                                                          LocalDateTime to) {
        if (requester.getRole() != UserRole.SUPER_ADMIN) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        return quoteRepository.searchAdminQuotes(
                        status, customerName, quoteNumber, writerName, from, to)
                .stream()
                .map(AdminQuoteListResponse::from)
                .toList();
    }

    // 담당 영업사원 견적(SALES_MANAGER, 동일 department)
    public List<AdminQuoteListResponse> searchManagerQuotes(User requester,
                                                            QuoteStatus status,
                                                            String customerName,
                                                            String quoteNumber,
                                                            String writerName,
                                                            LocalDateTime from,
                                                            LocalDateTime to) {
        if (requester.getRole() != UserRole.SALES_MANAGER) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        if (requester.getDepartment() == null || requester.getDepartment().isBlank()) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        return quoteRepository.searchManagerQuotes(
                        requester.getDepartment(), status, customerName, quoteNumber, writerName, from, to)
                .stream()
                .map(AdminQuoteListResponse::from)
                .toList();
    }

    public Quote getQuoteDetail(Long quoteId, User requester) {
        Quote quote = getQuoteWithDetailsOrThrow(quoteId);
        validateQuoteReadAccess(quote, requester);
        return quote;
    }

    public record InternalAnalysisResult(Quote quote, boolean approvalRequired, List<ApprovalReasonType> reasons) {}

    public InternalAnalysisResult getInternalAnalysis(Long quoteId, User requester) {
        Quote quote = getQuoteWithDetailsOrThrow(quoteId);
        validateQuoteReadAccess(quote, requester);

        List<QuoteItem> items = quoteItemRepository.findByQuoteIdOrderBySortOrderAsc(quoteId);
        List<ApprovalReasonType> reasons = approvalCheckService.check(
                quote.getDiscountPolicy(), items, quote.getTotalAmount(), quote.getProfitRate());

        return new InternalAnalysisResult(quote, !reasons.isEmpty(), reasons);
    }

    @Transactional
    public Quote reuseQuote(Long sourceQuoteId, User requester) {
        validateQuoteWriterRole(requester);
        validateTrainingCompleted(requester);
        Quote source = getQuoteWithDetailsOrThrow(sourceQuoteId);
        validateOwner(source, requester);

        Quote newQuote = Quote.builder()
                .createdBy(requester)
                .customer(source.getCustomer())
                .discountPolicy(source.getDiscountPolicy())
                .quoteNumber(generateQuoteNumber())
                .internalMemo(source.getInternalMemo())
                .issuedDate(LocalDate.now())
                .validUntil(LocalDate.now().plusMonths(1))
                .deliveryTerm(source.getDeliveryTerm())
                .status(QuoteStatus.DRAFT)
                .quoteCustomer(source.getQuoteCustomer())
                .company(source.getCompany())
                .build();

        quoteRepository.save(newQuote);
        List<QuoteItem> sourceItems = quoteItemRepository.findByQuoteIdOrderBySortOrderAsc(sourceQuoteId);
        List<QuoteItem> rebuiltItems = rebuildItemsWithCurrentPrice(newQuote, sourceItems);
        quoteItemRepository.saveAll(rebuiltItems);
        calculationService.calculate(newQuote, rebuiltItems);

        return newQuote;
    }

    @Transactional
    public Quote rewriteExpiredQuote(Long expiredQuoteId, User requester) {
        validateQuoteWriterRole(requester);
        validateTrainingCompleted(requester);
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
        List<QuoteItem> rebuiltItems = rebuildItemsWithCurrentPrice(newQuote,
                quoteItemRepository.findByQuoteIdOrderBySortOrderAsc(expiredQuoteId));
        quoteItemRepository.saveAll(rebuiltItems);
        calculationService.calculate(newQuote, rebuiltItems);

        return newQuote;
    }

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void expireOverdueQuotes() {
        quoteRepository.findExpiredQuotes(
                        Arrays.asList(QuoteStatus.APPROVAL_NOT_REQUIRED, QuoteStatus.APPROVED))
                .forEach(Quote::expire);
    }

    // 매일 자정, 3일 후 만료 예정인 견적의 작성자에게 만료 임박 알림을 발송한다.
    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void notifyExpiringQuotes() {
        LocalDate today = LocalDate.now();
        LocalDate until = today.plusDays(3);
        // 오늘~3일 후 만료 예정이면서 아직 알림을 보내지 않은 견적 (스케줄러 누락 시 catch-up)
        List<Quote> quotes = quoteRepository.findExpiringBetween(
                today, until,
                Arrays.asList(QuoteStatus.APPROVAL_NOT_REQUIRED, QuoteStatus.APPROVED, QuoteStatus.SENT));

        for (Quote quote : quotes) {
            // 한 건의 알림 실패가 나머지 배치를 중단시키지 않도록 항목별로 격리한다.
            try {
                long days = java.time.temporal.ChronoUnit.DAYS.between(today, quote.getValidUntil());
                String when = days <= 0 ? "오늘" : days + "일 후";
                notificationService.create(
                        quote.getCreatedBy().getId(),
                        com.project.back.notification.entity.NotificationType.QUOTE_EXPIRING,
                        "견적 만료 임박",
                        "견적 " + quote.getQuoteNumber() + " 이(가) " + when + " 만료됩니다.",
                        com.project.back.notification.entity.NotificationRelatedType.QUOTE,
                        quote.getId());
                // 중복 발송 방지 플래그 (알림 성공 시에만 기록)
                quote.markExpiringNotified(today);
            } catch (Exception e) {
                log.warn("만료 임박 알림 생성 실패 - quoteId={}, quoteNumber={}",
                        quote.getId(), quote.getQuoteNumber(), e);
            }
        }
    }

    public QuoteDetailResponse getQuote(String quoteNumber, Long userId) {
        User user = findUser(userId);
        Quote quote = quoteRepository.findByQuoteNumberAndCreatedByWithDetails(quoteNumber, user)
                .orElseThrow(() -> new CustomException(ErrorCode.QUOTE_NOT_FOUND));
        quoteRepository.findByIdWithApprovalReasons(quote.getId());
        return QuoteDetailResponse.from(quote);
    }

    /**
     * 고객 발송(이메일) 가능 여부 검증.
     * 승인 완료·승인 불필요·발송 완료(재발송)만 허용하며, EXPIRED 및 유효기간 경과 견적은 차단한다.
     */
    public void validateQuoteSendable(Quote quote) {
        LocalDate today = LocalDate.now();
        if (quote.getStatus() == QuoteStatus.EXPIRED) {
            throw new CustomException(ErrorCode.QUOTE_EXPIRED_NOT_SENDABLE);
        }
        if (quote.getIssuedDate() != null && today.isBefore(quote.getIssuedDate())) {
            throw new CustomException(ErrorCode.QUOTE_NOT_YET_ISSUED);
        }
        if (quote.getValidUntil() != null && quote.getValidUntil().isBefore(today)) {
            throw new CustomException(ErrorCode.QUOTE_VALIDITY_EXPIRED);
        }
        if (quote.getStatus() != QuoteStatus.APPROVED
                && quote.getStatus() != QuoteStatus.APPROVAL_NOT_REQUIRED
                && quote.getStatus() != QuoteStatus.SENT) {
            throw new CustomException(ErrorCode.QUOTE_NOT_SENDABLE);
        }
    }

    @Transactional
    public void markQuoteAsSent(Quote quote) {
        if (quote.getStatus() != QuoteStatus.SENT) {
            quote.markAsSent();
            userStatsUpdateService.recalculateAfterCommit(quote.getCreatedBy().getId());
        }
    }

    // 견적 작성용 — 제품 원가·적용 할인정책
    public QuoteProductContextResponse getProductContextForQuote(Long productId, Long userId) {
        User user = findUser(userId);
        validateQuoteWriterRole(user);
        validateTrainingCompleted(user);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        if (!product.isActive()) {
            throw new CustomException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        DiscountPolicy policy = resolveApplicablePolicy(product);
        return QuoteProductContextResponse.of(product, policy);
    }

    private DiscountPolicy resolveApplicablePolicy(Product product) {
        List<DiscountPolicy> candidates = discountPolicyRepository.findApplicableCandidates(
                product.getId(),
                product.getCategory().getId()
        );

        return candidates.stream()
                .min(Comparator.comparingInt(p -> switch (p.getTargetType()) {
                    case PRODUCT -> 1;
                    case CATEGORY -> 2;
                    case ALL -> 3;
                }))
                .orElse(null);
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

    // 소유자, SUPER_ADMIN(전체), SALES_MANAGER(동일 부서 영업사원)
    private void validateQuoteReadAccess(Quote quote, User requester) {
        if (quote.getCreatedBy().getId().equals(requester.getId())) {
            return;
        }
        if (requester.getRole() == UserRole.SUPER_ADMIN) {
            return;
        }
        if (requester.getRole() == UserRole.SALES_MANAGER) {
            User writer = quote.getCreatedBy();
            if (writer.getRole() != UserRole.SALES_STAFF) {
                throw new CustomException(ErrorCode.QUOTE_ACCESS_DENIED);
            }
            String dept = requester.getDepartment();
            if (dept == null || dept.isBlank()
                    || writer.getDepartment() == null
                    || !dept.equals(writer.getDepartment())) {
                throw new CustomException(ErrorCode.QUOTE_ACCESS_DENIED);
            }
            return;
        }
        throw new CustomException(ErrorCode.QUOTE_ACCESS_DENIED);
    }

    private void validateEditable(Quote quote) {
        if (quote.getStatus() != QuoteStatus.DRAFT && quote.getStatus() != QuoteStatus.REVISING)
            throw new CustomException(ErrorCode.QUOTE_NOT_EDITABLE);
    }

    private void validateTrainingCompleted(User user) {
        if (!trainingService.isTrainingCompleted(user)) {
            throw new CustomException(ErrorCode.TRAINING_NOT_COMPLETED);
        }
    }

    private void validateQuoteDates(LocalDate issuedDate, LocalDate validUntil) {
        if (validUntil == null) {
            throw new CustomException(ErrorCode.QUOTE_VALID_UNTIL_REQUIRED);
        }
        if (issuedDate != null && validUntil.isBefore(issuedDate)) {
            throw new CustomException(ErrorCode.QUOTE_VALID_UNTIL_INVALID);
        }
        if (validUntil.isBefore(LocalDate.now())) {
            throw new CustomException(ErrorCode.QUOTE_VALID_UNTIL_INVALID);
        }
    }

    // 견적 작성·수정 — 영업사원·영업관리자만 (최고관리자 불가)
    private void validateQuoteWriterRole(User user) {
        if (user.getRole() == UserRole.SUPER_ADMIN) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
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

    private List<QuoteItem> buildItems(Quote quote,
                                       List<QuoteItemCommand> commands,
                                       DiscountPolicy policy,
                                       Map<Long, QuoteItem> existingByProductId) {
        int[] order = {0};
        return commands.stream()
                .map(cmd -> {
                    var product = productRepository.findById(cmd.productId())
                            .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
                    if (!product.isActive()) throw new CustomException(ErrorCode.PRODUCT_NOT_FOUND);

                    ItemPriceSnapshot prices = resolveItemPrices(product, existingByProductId);

                    validateDiscountReasonAgainstPolicy(
                            policy, cmd.discountRate(), cmd.discountReason(), cmd.quantity(),
                            prices.unitPrice(), prices.costPrice());

                    QuoteItem item = QuoteItem.builder()
                            .quote(quote)
                            .productId(product.getId())
                            .productName(product.getName())
                            .productCode(product.getCode())
                            .spec(product.getSpec())
                            .unitPrice(prices.unitPrice())
                            .costPrice(prices.costPrice())
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

    /** 신규 초안은 제품 마스터 가격, 수정·재작성은 기존 품목 스냅샷(신규 품목만 마스터) */
    private ItemPriceSnapshot resolveItemPrices(Product product, Map<Long, QuoteItem> existingByProductId) {
        if (existingByProductId != null) {
            QuoteItem existing = existingByProductId.get(product.getId());
            if (existing != null) {
                return new ItemPriceSnapshot(existing.getUnitPrice(), existing.getCostPrice());
            }
        }
        return new ItemPriceSnapshot(product.getUnitPrice(), product.getCostPrice());
    }

    private record ItemPriceSnapshot(BigDecimal unitPrice, BigDecimal costPrice) {}

     //할인율이 정책의 최대 할인율을 초과하거나,할인 적용 후 예상 이익률이 정책의 최소 이익률보다 낮으면 할인 사유를 필수로 요구한다.
     // 정책이 없는 경우(discountPolicyId가 null)에는 검증을 생략한다.
     // 기존 메서드를 수정하여 인자를 받도록 변경
     private void validateDiscountReasonAgainstPolicy(DiscountPolicy policy,
                                                      BigDecimal discountRate,
                                                      String discountReason,
                                                      BigDecimal quantity,
                                                      BigDecimal unitPrice,
                                                      BigDecimal costPrice) {
         if (policy == null) return;

         BigDecimal rate = discountRate != null ? discountRate : BigDecimal.ZERO;

         // 정책 위반 여부 계산 로직
         boolean exceedsMaxDiscount = policy.getMaxDiscountRate() != null
                 && rate.compareTo(policy.getMaxDiscountRate()) > 0;

         BigDecimal base = unitPrice.multiply(quantity);
         BigDecimal discountAmount = rate.compareTo(BigDecimal.ZERO) > 0
                 ? base.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                 : BigDecimal.ZERO;
         BigDecimal lineSupply = base.subtract(discountAmount);
         BigDecimal lineCost = costPrice.multiply(quantity);
         BigDecimal profit = lineSupply.subtract(lineCost);
         BigDecimal profitRate = lineSupply.compareTo(BigDecimal.ZERO) == 0
                 ? BigDecimal.ZERO
                 : profit.multiply(BigDecimal.valueOf(100)).divide(lineSupply, 2, RoundingMode.HALF_UP);

         boolean belowMinProfit = policy.getMinProfitRate() != null
                 && profitRate.compareTo(policy.getMinProfitRate()) < 0;

         // 사유 검증
         if ((exceedsMaxDiscount || belowMinProfit)
                 && (discountReason == null || discountReason.isBlank())) {
             throw new CustomException(ErrorCode.DISCOUNT_REASON_REQUIRED);
         }
     }


     // reuseQuote·rewriteExpiredQuote 시 사용 — 품목 구성은 유지하고 단가·원가는 현재 제품 마스터 기준
     private List<QuoteItem> rebuildItemsWithCurrentPrice(Quote newQuote, List<QuoteItem> sourceItems) {
         int[] order = {0};
         return sourceItems.stream()
                 .map(src -> {
                     //비활성 상품은 가져오지 않도록 filter 추가
                     Product currentProduct = src.getProductId() != null
                             ? productRepository.findById(src.getProductId())
                               .filter(Product::isActive)
                               .orElse(null)
                             : null;

                     // 만약 필수 제품이 비활성화되었다면 예외 처리하거나 null로 넘김
                     if (src.getProductId() != null && currentProduct == null) {
                         throw new CustomException(ErrorCode.PRODUCT_NOT_FOUND);
                     }

                     //재작성 시에도 정책 검증 실시
                     if (currentProduct != null) {
                         BigDecimal unitPrice = currentProduct.getUnitPrice();
                         BigDecimal costPrice = currentProduct.getCostPrice();
                         validateDiscountReasonAgainstPolicy(
                                 newQuote.getDiscountPolicy(),
                                 src.getDiscountRate(),
                                 src.getDiscountReason(),
                                 src.getQuantity(),
                                 unitPrice,
                                 costPrice
                         );
                     }

                     QuoteItem item = QuoteItem.builder()
                             .quote(newQuote)
                             .productId(src.getProductId())
                             .productName(currentProduct != null ? currentProduct.getName() : src.getProductName())
                             .productCode(currentProduct != null ? currentProduct.getCode() : src.getProductCode())
                             .spec(currentProduct != null ? currentProduct.getSpec() : src.getSpec())
                             .unitPrice(currentProduct != null ? currentProduct.getUnitPrice() : src.getUnitPrice())
                             .costPrice(currentProduct != null ? currentProduct.getCostPrice() : src.getCostPrice())
                             .quantity(src.getQuantity())
                             .discountRate(src.getDiscountRate())
                             .discountReason(src.getDiscountReason())
                             .vatApplicable(src.getVatApplicable())
                             .sortOrder(order[0]++)
                             .build();

                     newQuote.addItem(item);
                     return item;
                 })
                 .toList();
     }

    //전체 이익률 검증 메서드
    //항목별 검증과 견적 합계 검증이 어긋나지 않게 제출 시점에 quote.getProfitRate()를 기준으로 승인 여부를 재확인하는 로직을 추가하거나
    // validation 메서드를 통일
    private void validateTotalProfitAgainstPolicy(DiscountPolicy policy, Quote quote) {
        if (policy == null) return;

        // 견적 합계 이익률과 정책의 최소 이익률 비교
        if (quote.getProfitRate().compareTo(policy.getMinProfitRate()) < 0) {
            // 필요시 로직 처리
        }
    }

    public record QuoteItemCommand(
            @NotNull(message = "제품 ID는 필수입니다.") Long productId,
            @NotBlank(message = "제품명은 필수입니다.") String productName,
            String productCode,
            @Size(max = 200) String spec,
            @NotNull(message = "단가는 필수입니다.") @DecimalMin("0") BigDecimal unitPrice,
            @NotNull(message = "원가는 필수입니다.") @DecimalMin("0") BigDecimal costPrice,
            @NotNull(message = "수량은 필수입니다.") @DecimalMin("0.01") BigDecimal quantity,
            @DecimalMin("0") @DecimalMax("100") BigDecimal discountRate,
            @NotNull(message = "VAT 적용 여부는 필수입니다.") Boolean vatApplicable,
            @Size(max = 255, message = "할인 사유는 255자 이내로 입력해주세요.")
            String discountReason
    ) {}

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
