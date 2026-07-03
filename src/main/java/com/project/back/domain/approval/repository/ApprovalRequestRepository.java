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
        JOIN FETCH ar.quote
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
        JOIN FETCH ar.quote
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

    // 특정 부서 소속 영업사원의 승인 대기 목록 조회 (SALES_MANAGER 담당 범위)
    @Query("""
        SELECT ar FROM ApprovalRequest ar
        JOIN FETCH ar.quote
        JOIN FETCH ar.requester r
        LEFT JOIN FETCH ar.approver
        WHERE ar.status = :status
        AND r.department = :department
        ORDER BY ar.requestedAt ASC
        """)
    List<ApprovalRequest> findByStatusAndRequesterDepartment(
            @Param("status") ApprovalRequest.ApprovalStatus status,
            @Param("department") String department
    );

    // 처리 완료 목록 조회 (상태/기간/처리자/부서로 필터링, 각 조건은 null이면 무시)
    // - status가 null이면 전체 상태(대기/승인/반려) 조회
    // - PENDING 건은 아직 처리되지 않아 기간이 지나도 계속 조치가 필요하므로 기간 필터를 적용하지 않고,
    //   승인/반려된(처리 완료) 건만 processedAt 기준으로 기간 필터링한다
    @Query("""
        SELECT ar FROM ApprovalRequest ar
        JOIN FETCH ar.quote
        JOIN FETCH ar.requester r
        LEFT JOIN FETCH ar.approver
        WHERE (:status IS NULL OR ar.status = :status)
        AND (:department IS NULL OR r.department = :department)
        AND (:approverId IS NULL OR ar.approver.id = :approverId)
        AND (:from IS NULL OR ar.status = 'PENDING' OR ar.processedAt >= :from)
        AND (:to IS NULL OR ar.status = 'PENDING' OR ar.processedAt <= :to)
        ORDER BY COALESCE(ar.processedAt, ar.requestedAt) DESC
        """)
    List<ApprovalRequest> search(
            @Param("status") ApprovalRequest.ApprovalStatus status,
            @Param("department") String department,
            @Param("approverId") Long approverId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    // SLA 초과 대상 조회 — PENDING 상태이면서 요청일이 threshold 이전인 건 (스케줄러가 매일 호출)
    @Query("""
        SELECT ar FROM ApprovalRequest ar
        JOIN FETCH ar.quote
        JOIN FETCH ar.requester
        WHERE ar.status = 'PENDING'
        AND ar.requestedAt <= :threshold
        """)
    List<ApprovalRequest> findPendingRequestedBefore(@Param("threshold") LocalDateTime threshold);
}