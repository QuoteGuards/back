package com.project.back.domain.customer.repository;

import com.project.back.domain.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // 권한 검증용 단건 조회
    @Query("SELECT c FROM Customer c WHERE c.id = :id AND c.createdBy.id = :userId")
    Optional<Customer> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    // 내가 등록한 고객 중 회사명으로 검색 (견적 작성 화면 자동완성용)
    List<Customer> findByCreatedByIdAndCompanyNameContainingIgnoreCase(Long userId, String companyName);

    // 내가 등록한 전체 고객 목록
    List<Customer> findByCreatedById(Long userId);
}
