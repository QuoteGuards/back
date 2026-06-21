package com.project.back.domain.customer.repository;

import com.project.back.domain.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // 내가 등록한 고객 중 회사명으로 검색 (견적 작성 화면 자동완성용)
    List<Customer> findByCreatedByIdAndCompanyNameContainingIgnoreCase(Long userId, String companyName);

    // 내가 등록한 전체 고객 목록
    List<Customer> findByCreatedById(Long userId);
}
