package com.project.back.domain.product.entity;

import com.project.back.domain.category.entity.Category;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
@AllArgsConstructor(access=AccessLevel.PRIVATE)
@Builder
public class Product {

    // id
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 카테고리
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="category_id", nullable=false)
    private Category category;

    // 제품 이름
    @Column(nullable=false, length=255)
    private String name;

    // 제품 코드
    @Column(nullable=false, length=100, unique=true)
    private String code;

    // 제품 설명
    @Column(columnDefinition="TEXT")
    private String description;

    // 제품 규격
    @Column(length=100)
    private String spec;

    // 제품 이미지
    @Column(length=500)
    private String imageUrl;

    //제품 판매 단가
    @Column(nullable=false, precision=15, scale=2)
    private BigDecimal unitPrice;

    // 제품 원가
    @Column(nullable=false, precision=15, scale=2)
    private BigDecimal costPrice;

    // 판매 단위
    @Column(nullable=false, length=20)
    @Builder.Default
    private String unit="EA";

    // vat 적용 여부
    @Column(nullable = false)
    @Builder.Default
    private boolean vatApplicable = true;

    // 활성화/비활성화
    @Column(nullable = false)
    @Builder.Default
    private boolean isActive = true;

    // 조회수(제품 인기 통계에 활용)
    @Column(nullable = false)
    @Builder.Default
    private int viewCount = 0;

    // 제품 생성 일시
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 제품 수정 일시
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;


    public void update(Category category, String name, String code,
                       String description, String spec, String imageUrl,
                       BigDecimal unitPrice, BigDecimal costPrice,
                       String unit, boolean vatApplicable) {
        this.category = category;
        this.name = name;
        this.code = code;
        this.description = description;
        this.spec = spec;
        this.imageUrl = imageUrl;
        this.unitPrice = unitPrice;
        this.costPrice = costPrice;
        this.unit = unit;
        this.vatApplicable = vatApplicable;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }

    public void increaseViewCount() {
        this.viewCount++;
    }

}
