package com.project.back.domain.quote.entity;

import com.project.back.domain.discount.entity.DiscountPolicy;
import com.project.back.domain.approval.entity.QuoteApprovalReason;
import com.project.back.domain.customer.entity.Customer;
import com.project.back.domain.user.entity.User;
import com.project.back.global.enums.QuoteStatus;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quotes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Quote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discount_policy_id")
    private DiscountPolicy discountPolicy;

    @Column(name = "quote_number", nullable = false, unique = true, length = 50)
    private String quoteNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Embedded
    private QuoteCustomer quoteCustomer;

    @Embedded
    private QuoteCompany company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_quote_id")
    private Quote originalQuote;

    @Column(name = "version_no", nullable = false)
    @Builder.Default
    private Integer versionNo = 1;

    @Column(name = "is_latest", nullable = false)
    @Builder.Default
    private Boolean isLatest = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private QuoteStatus status = QuoteStatus.DRAFT;

    @Column(name = "issued_date")
    private LocalDate issuedDate;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "delivery_term", length = 100)
    private String deliveryTerm;

    @Column(name = "subtotal", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "supply_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal supplyAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "total_cost_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalCostAmount = BigDecimal.ZERO;

    @Column(name = "expected_profit_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal expectedProfitAmount = BigDecimal.ZERO;

    @Column(name = "profit_rate", nullable = false, precision = 7, scale = 2)
    @Builder.Default
    private BigDecimal profitRate = BigDecimal.ZERO;

    @Column(name = "approval_required", nullable = false)
    @Builder.Default
    private Boolean approvalRequired = false;

    @Column(name = "internal_memo", columnDefinition = "TEXT")
    private String internalMemo;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    // 만료 임박 알림을 보낸 날짜. 중복 발송(스케줄러 catch-up) 방지용
    @Column(name = "notified_expiring_at")
    private LocalDate notifiedExpiringAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<QuoteItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<QuoteApprovalReason> approvalReasons = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void updateCalculation(BigDecimal subtotal, BigDecimal discountAmount,
                                  BigDecimal supplyAmount, BigDecimal taxAmount,
                                  BigDecimal totalAmount, BigDecimal totalCostAmount,
                                  BigDecimal expectedProfitAmount, BigDecimal profitRate) {
        this.subtotal = subtotal;
        this.discountAmount = discountAmount;
        this.supplyAmount = supplyAmount;
        this.taxAmount = taxAmount;
        this.totalAmount = totalAmount;
        this.totalCostAmount = totalCostAmount;
        this.expectedProfitAmount = expectedProfitAmount;
        this.profitRate = profitRate;
    }

    public void updateInfo(Customer customer, QuoteCustomer snapshot, String internalMemo,
                           LocalDate issuedDate, LocalDate validUntil, String deliveryTerm) {
        this.customer = customer;
        this.quoteCustomer = snapshot; //스냅샷 동기화
        this.internalMemo = internalMemo;
        this.issuedDate = issuedDate;
        this.validUntil = validUntil;
        this.deliveryTerm = deliveryTerm;
    }

    public void complete(boolean approvalRequired) {
        this.approvalRequired = approvalRequired;
        this.submittedAt = LocalDateTime.now();
        this.status = approvalRequired ? QuoteStatus.APPROVAL_PENDING : QuoteStatus.APPROVAL_NOT_REQUIRED;
    }

    public void saveAsDraft() {
        this.status = QuoteStatus.DRAFT;
    }

    public void startRevising() {
        this.status = QuoteStatus.REVISING;
    }

    public void expire() {
        this.status = QuoteStatus.EXPIRED;
        this.expiredAt = LocalDateTime.now();
    }

    public void markAsApproved() {
        this.status = QuoteStatus.APPROVED;
        this.approvedAt = LocalDateTime.now();
    }

    public void markAsSent() {
        this.status = QuoteStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    public void markExpiringNotified(LocalDate notifiedAt) {
        this.notifiedExpiringAt = notifiedAt;
    }

    public void markAsNotLatest() {
        this.isLatest = false;
    }

    // 취소 가능 상태: DRAFT, SUBMITTED, APPROVAL_NOT_REQUIRED, APPROVAL_PENDING, APPROVED, REJECTED, REVISING
    // 취소 불가 상태: SENT(고객 발송 완료), EXPIRED, CANCELLED(이미 취소됨)
    public void cancel() {
        if (status == QuoteStatus.SENT || status == QuoteStatus.EXPIRED || status == QuoteStatus.CANCELLED) {
            throw new CustomException(ErrorCode.QUOTE_NOT_CANCELLABLE);
        }
        this.status = QuoteStatus.CANCELLED;
    }

    public void addItem(QuoteItem item) {
        items.add(item);
        item.assignQuote(this);
    }

    //리스트 교체용 메서드
    public void replaceItems(List<QuoteItem> newItems) {
        this.items.clear();
        if (newItems != null) {
            newItems.forEach(this::addItem);
        }
    }
}