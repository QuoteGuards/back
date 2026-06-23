package com.project.back.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// @Version: 동일 사용자 통계 동시 갱신 시 마지막 커밋 승리(last-write-wins) 대신
// OptimisticLockException을 발생시켜 데이터 유실을 방지한다.

@Entity
@Table(name = "user_stats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "total_quotes", nullable = false)
    @Builder.Default
    private int totalQuotes = 0;

    @Column(name = "approved_quotes", nullable = false)
    @Builder.Default
    private int approvedQuotes = 0;

    @Column(name = "rejected_quotes", nullable = false)
    @Builder.Default
    private int rejectedQuotes = 0;

    @Column(name = "sent_quotes", nullable = false)
    @Builder.Default
    private int sentQuotes = 0;

    @Column(name = "total_amount", nullable = false, precision = 20, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "total_supply_amount", nullable = false, precision = 20, scale = 2)
    @Builder.Default
    private BigDecimal totalSupplyAmount = BigDecimal.ZERO;

    @Column(name = "total_profit_amount", nullable = false, precision = 20, scale = 2)
    @Builder.Default
    private BigDecimal totalProfitAmount = BigDecimal.ZERO;

    @Column(name = "average_discount_rate", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal averageDiscountRate = BigDecimal.ZERO;

    @Column(name = "average_profit_rate", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal averageProfitRate = BigDecimal.ZERO;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void update(int totalQuotes, int approvedQuotes, int rejectedQuotes, int sentQuotes,
                       BigDecimal totalAmount, BigDecimal totalSupplyAmount, BigDecimal totalProfitAmount,
                       BigDecimal averageDiscountRate, BigDecimal averageProfitRate) {
        this.totalQuotes = totalQuotes;
        this.approvedQuotes = approvedQuotes;
        this.rejectedQuotes = rejectedQuotes;
        this.sentQuotes = sentQuotes;
        this.totalAmount = totalAmount;
        this.totalSupplyAmount = totalSupplyAmount;
        this.totalProfitAmount = totalProfitAmount;
        this.averageDiscountRate = averageDiscountRate;
        this.averageProfitRate = averageProfitRate;
    }
}
