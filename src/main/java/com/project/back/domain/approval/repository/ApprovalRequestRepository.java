package com.project.back.domain.approval.repository;

import com.project.back.domain.approval.entity.ApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    // 특정 견적의 승인 요청 조회
    Optional<ApprovalRequest> findByQuote_Id(Long quoteId);

    // 특정 견적의 특정 상태 승인 요청 조회
    Optional<ApprovalRequest> findByQuote_IdAndStatus(
            Long quoteId,
            ApprovalRequest.ApprovalStatus status
    );

    // 승인 대기 목록 조회 (요청일 오름차순)
    @Query("""
        SELECT ar FROM ApprovalRequest ar
        JOIN FETCH ar.requester
        LEFT JOIN FETCH ar.approver
        WHERE ar.status = :status
        ORDER BY ar.requestedAt ASC
        """)
    List<ApprovalRequest> findByStatusOrderByRequestedAtAsc(
            ApprovalRequest.ApprovalStatus status
    );

    // 특정 영업사원의 승인 요청 목록 조회
    List<ApprovalRequest> findByRequesterIdOrderByRequestedAtDesc(Long requesterId);

    // 특정 견적에 특정 상태 승인 요청 존재 여부 확인
    boolean existsByQuote_IdAndStatus(
            Long quoteId,
            ApprovalRequest.ApprovalStatus status
    );

    @Query("""
        SELECT ar FROM ApprovalRequest ar
        JOIN FETCH ar.requester
        LEFT JOIN FETCH ar.approver
        WHERE ar.id = :id
        """)
    Optional<ApprovalRequest> findByIdWithUsers(@Param("id") Long id);

    long countByStatusAndProcessedAtBetween(
            ApprovalRequest.ApprovalStatus status,
            LocalDateTime from,
            LocalDateTime to
    );
}