package com.project.back.domain.approval.repository;

import com.project.back.domain.approval.entity.ApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    // 특정 견적의 승인 요청 조회
    Optional<ApprovalRequest> findByQuoteId(Long quoteId);

    // 특정 견적의 특정 상태 승인 요청 조회
    Optional<ApprovalRequest> findByQuoteIdAndStatus(
            Long quoteId,
            ApprovalRequest.ApprovalStatus status
    );

    // 승인 대기 목록 조회 (요청일 오름차순)
    List<ApprovalRequest> findByStatusOrderByRequestedAtAsc(
            ApprovalRequest.ApprovalStatus status
    );

    // 특정 영업사원의 승인 요청 목록 조회
    List<ApprovalRequest> findByRequesterIdOrderByRequestedAtDesc(Long requesterId);

    // 특정 견적에 특정 상태 승인 요청 존재 여부 확인
    boolean existsByQuoteIdAndStatus(
            Long quoteId,
            ApprovalRequest.ApprovalStatus status
    );
}