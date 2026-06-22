package com.project.back.domain.discount.repository;

import com.project.back.domain.discount.entity.DiscountPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DiscountPolicyRepository extends JpaRepository<DiscountPolicy, Long> {

    //활성화된 정책 중 가장 최근 것 조회
    Optional<DiscountPolicy> findFirstByIsActiveTrueOrderByCreatedAtDesc();
}
