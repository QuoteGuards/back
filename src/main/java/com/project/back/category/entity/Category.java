package com.project.back.category.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "categories")
@Getter
@NoArgsConstructor(access= AccessLevel.PROTECTED)
@AllArgsConstructor(access= AccessLevel.PRIVATE)
@Builder
public class Category {

    // 카테고리 식별자
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 상위 카테고리의 id, null 이면 대분류
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="parent_id")
    private Category parent;

    // 하위 카테고리 목록 조회를 위함
    @OneToMany(mappedBy = "parent")
    @Builder.Default
    private List<Category> children = new ArrayList<>();

    // 카테고리명
    @Column(nullable = false, length = 100)
    private String name;

    // URL 또는 검색에 사용할 카테고리 식별 문자열
    @Column(nullable = false, length = 100, unique = true)
    private String slug;

    // 카테고리 깊이 1=대분류, 2=중분류, 3=소분류
    @Column(nullable = false)
    private int depth;

    // 카테고리 정렬 순서
    @Column(nullable = false)
    private int sortOrder;

    // 카테고리 사용 여부
    @Column(nullable = false)
    private boolean isActive;

    // 카테고리 생성 일시
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 카테고리 수정 일시
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

}
