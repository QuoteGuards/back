package com.project.back.domain.approval.controller;

import com.project.back.domain.approval.dto.request.ApprovalDecisionDto;
import com.project.back.domain.approval.dto.request.ApprovalRequestDto;
import com.project.back.domain.approval.dto.request.ReRequestDto;
import com.project.back.domain.approval.dto.request.UpdateMemoDto;
import com.project.back.domain.approval.dto.response.ApprovalHistoryResponse;
import com.project.back.domain.approval.dto.response.ApprovalMonthlyStatsResponse;
import com.project.back.domain.approval.dto.response.ApprovalReasonResponse;
import com.project.back.domain.approval.dto.response.ApprovalRequestDetailResponse;
import com.project.back.domain.approval.dto.response.ApprovalRequestResponse;
import com.project.back.domain.approval.entity.ApprovalRequest;

import com.project.back.domain.approval.service.ApprovalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ApprovalController {

    private final ApprovalService approvalService;

    // ── 1. 승인 요청 ──
    // POST /api/quotes/{quoteId}/approval-requests
    @PreAuthorize("hasRole('SALES_STAFF')")
    @PostMapping("/quotes/{quoteId}/approval-requests")
    public ResponseEntity<ApprovalRequestResponse> requestApproval(
            @PathVariable Long quoteId,
            @RequestBody @Valid ApprovalRequestDto request,
            @AuthenticationPrincipal Long userId
    ) {
        ApprovalRequest result = approvalService.requestApproval(
                quoteId, userId, request.getRequestMemo()
        );
        return ResponseEntity.ok(ApprovalRequestResponse.from(result));
    }

    // 2. 승인 요청 상세 조회 추가
    @PreAuthorize("hasAnyRole('SALES_STAFF', 'SALES_MANAGER', 'SUPER_ADMIN')")
    @GetMapping("/approval-requests/{approvalRequestId}")
    public ResponseEntity<ApprovalRequestDetailResponse> getApprovalDetail(
            @PathVariable Long approvalRequestId
    ) {
        ApprovalRequestDetailResponse result =
                approvalService.getApprovalDetail(approvalRequestId);
        return ResponseEntity.ok(result);
    }


    // ── 3. 이달 승인/반려 통계 (관리자용) ──
    // GET /api/admin/approval-requests/stats
    @PreAuthorize("hasAnyRole('SALES_MANAGER', 'SUPER_ADMIN')")
    @GetMapping("/admin/approval-requests/stats")
    public ResponseEntity<ApprovalMonthlyStatsResponse> getMonthlyStats() {
        return ResponseEntity.ok(approvalService.getMonthlyStats());
    }

    // ── 4. 승인 대기 목록 조회 (관리자용) ──
    // GET /api/admin/approval-requests
    @PreAuthorize("hasAnyRole('SALES_MANAGER', 'SUPER_ADMIN')")
    @GetMapping("/admin/approval-requests")
    public ResponseEntity<List<ApprovalRequestResponse>> getPendingList() {
        List<ApprovalRequestResponse> result = approvalService.getPendingList()
                .stream()
                .map(ApprovalRequestResponse::from)
                .toList();
        return ResponseEntity.ok(result);
    }

    // ── 4. 승인 처리 ──
    // POST /api/admin/quotes/{quoteId}/approve
    @PreAuthorize("hasAnyRole('SALES_MANAGER', 'SUPER_ADMIN')")
    @PostMapping("/admin/quotes/{quoteId}/approve")
    public ResponseEntity<ApprovalRequestResponse> approve(
            @PathVariable Long quoteId,
            @RequestBody @Valid ApprovalDecisionDto request,
            @AuthenticationPrincipal Long userId
    ) {
        ApprovalRequest result = approvalService.approve(
                request.getApprovalRequestId(),
                userId,
                request.getMemo());
        return ResponseEntity.ok(ApprovalRequestResponse.from(result));
    }

    // ── 5. 반려 처리 ──
    // POST /api/admin/quotes/{quoteId}/reject
    @PreAuthorize("hasAnyRole('SALES_MANAGER', 'SUPER_ADMIN')")
    @PostMapping("/admin/quotes/{quoteId}/reject")
    public ResponseEntity<ApprovalRequestResponse> reject(
            @PathVariable Long quoteId,
            @RequestBody @Valid ApprovalDecisionDto request,
            @AuthenticationPrincipal Long userId
    ) {
        ApprovalRequest result = approvalService.reject(
                request.getApprovalRequestId(),
                userId,
                request.getRejectReason());
        return ResponseEntity.ok(ApprovalRequestResponse.from(result));
    }

    // ── 6. 재요청 ──
    // POST /api/quotes/{quoteId}/resubmit
    @PreAuthorize("hasRole('SALES_STAFF')")
    @PostMapping("/quotes/{quoteId}/resubmit")
    public ResponseEntity<ApprovalRequestResponse> reRequest(
            @PathVariable Long quoteId,
            @RequestBody @Valid ReRequestDto request,
            @AuthenticationPrincipal Long userId
    ) {
        ApprovalRequest result = approvalService.reRequest(
                request.getApprovalRequestId(),
                userId,
                request.getRequestMemo());
        return ResponseEntity.ok(ApprovalRequestResponse.from(result));
    }

    // ── 7. 승인 요청 메모 수정 ──
    // PATCH /api/quotes/{quoteId}/approval-requests/{approvalRequestId}/memo
    @PreAuthorize("hasRole('SALES_STAFF')")
    @PatchMapping("/quotes/{quoteId}/approval-requests/{approvalRequestId}/memo")
    public ResponseEntity<Void> updateMemo(
            @PathVariable Long quoteId,
            @PathVariable Long approvalRequestId,
            @RequestBody UpdateMemoDto request,
            @AuthenticationPrincipal Long userId
    ) {
        approvalService.updateMemo(approvalRequestId, userId, request.getRequestMemo());
        return ResponseEntity.ok().build();
    }

    // ── 8. 승인 이력 조회 ──
    // GET /api/quotes/{quoteId}/approval-histories
    @PreAuthorize("hasAnyRole('SALES_STAFF', 'SALES_MANAGER', 'SUPER_ADMIN')")
    @GetMapping("/quotes/{quoteId}/approval-histories")
    public ResponseEntity<List<ApprovalHistoryResponse>> getApprovalHistories(
            @PathVariable Long quoteId
    ) {
        List<ApprovalHistoryResponse> result = approvalService.getApprovalHistories(quoteId)
                .stream()
                .map(ApprovalHistoryResponse::from)
                .toList();
        return ResponseEntity.ok(result);
    }

    // ── 8. 승인 필요 사유 조회 ──
    // GET /api/quotes/{quoteId}/approval-reasons
    @PreAuthorize("hasAnyRole('SALES_STAFF', 'SALES_MANAGER', 'SUPER_ADMIN')")
    @GetMapping("/quotes/{quoteId}/approval-reasons")
    public ResponseEntity<List<ApprovalReasonResponse>> getApprovalReasons(
            @PathVariable Long quoteId
    ) {
        List<ApprovalReasonResponse> result = approvalService.getApprovalReasons(quoteId)
                .stream()
                .map(ApprovalReasonResponse::from)
                .toList();
        return ResponseEntity.ok(result);
    }
}