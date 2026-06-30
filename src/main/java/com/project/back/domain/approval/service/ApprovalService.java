package com.project.back.domain.approval.service;

import com.project.back.domain.approval.dto.response.ApprovalRequestDetailResponse;
import com.project.back.domain.approval.entity.ApprovalRequest;
import com.project.back.domain.approval.entity.QuoteApprovalHistory;
import com.project.back.domain.approval.entity.QuoteApprovalReason;
import com.project.back.domain.approval.repository.ApprovalRequestRepository;
import com.project.back.domain.approval.repository.QuoteApprovalHistoryRepository;
import com.project.back.domain.approval.repository.QuoteApprovalReasonRepository;
import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.quote.repository.QuoteRepository;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.entity.UserRole;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.domain.user.service.UserStatsUpdateService;
import com.project.back.global.enums.ApprovalReasonType;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.back.domain.approval.dto.response.ApprovalMonthlyStatsResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApprovalService {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final QuoteApprovalReasonRepository quoteApprovalReasonRepository;
    private final QuoteApprovalHistoryRepository quoteApprovalHistoryRepository;
    private final UserRepository userRepository;
    private final QuoteRepository quoteRepository;
    private final UserStatsUpdateService userStatsUpdateService;

    // ── 1. 승인 요청 ──
    @Transactional
    public ApprovalRequest requestApproval(Long quoteId, Long requesterId, String requestMemo) {

        // TODO: training 팀원 UserTrainingProgress 완성 후 LMS 교육 이수 여부 체크 추가
        // boolean isCompleted = userTrainingProgressRepository
        //         .existsByUserIdAndStatus(requesterId, UserTrainingProgress.Status.COMPLETED);
        // if (!isCompleted) {
        //     throw new IllegalStateException("필수 교육을 이수해야 승인 요청이 가능합니다.");
        // }

        // 이미 PENDING 상태 승인 요청이 있으면 중복 요청 방지
        if (approvalRequestRepository.existsByQuote_IdAndStatus(
                quoteId, ApprovalRequest.ApprovalStatus.PENDING)) {
            throw new CustomException(ErrorCode.APPROVAL_ALREADY_PENDING);
        }

        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 승인 요청 생성
        Quote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new CustomException(ErrorCode.QUOTE_NOT_FOUND));

        ApprovalRequest approvalRequest = ApprovalRequest.builder()
                .quote(quote)
                .requester(requester)
                .requestMemo(requestMemo)
                .status(ApprovalRequest.ApprovalStatus.PENDING) // 승인 대기시 명시적으로 PENDING 설정
                .build();

        approvalRequestRepository.save(approvalRequest);


        // 승인 이력 저장 (REQUESTED)
        saveHistory(
                approvalRequest,
                requester,
                QuoteApprovalHistory.ActionType.REQUESTED,
                null,
                ApprovalRequest.ApprovalStatus.PENDING,
                requestMemo
        );

        return approvalRequest;
    }

    // ── 2. 승인 처리 ──
    @Transactional
    public ApprovalRequest approve(Long approvalRequestId, Long approverId, String memo) {

        ApprovalRequest approvalRequest = findApprovalRequestById(approvalRequestId);
        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 자가 승인 차단
        validateSelfApproval(approver, approvalRequest);

        // SALES_MANAGER는 자신의 부서 영업사원 견적만 승인 가능 (관리자→관리자는 부서 무관)
        validateDepartmentIfManager(approver, approvalRequest);

        // PENDING 상태만 승인 가능
        validatePendingStatus(approvalRequest);

        ApprovalRequest.ApprovalStatus beforeStatus = approvalRequest.getStatus();

        // 승인 처리
        approvalRequest.approve(approver, memo);
        approvalRequestRepository.save(approvalRequest);

        // [견적 파트 오케스트레이션 연동]: 연관된 견적서를 찾아 확정 상태로 동기화
        Quote quote = quoteRepository.findById(approvalRequest.getQuote().getId())
                .orElseThrow(() -> new CustomException(ErrorCode.QUOTE_NOT_FOUND));

        // 승인 완료 상태로 전이. 이후 이메일 발송 시 SENT로 변경된다.
        quote.markAsApproved();

        // 승인 이력 저장
        saveHistory(
                approvalRequest,
                approver,
                QuoteApprovalHistory.ActionType.APPROVED,
                beforeStatus,
                ApprovalRequest.ApprovalStatus.APPROVED,
                memo
        );

        // 견적 작성자 통계 갱신 (승인 + 발송 카운트 반영) - 커밋 이후 재집계
        userStatsUpdateService.recalculateAfterCommit(quote.getCreatedBy().getId());

        return approvalRequest;
    }

    // ── 3. 반려 처리 ──
    @Transactional
    public ApprovalRequest reject(Long approvalRequestId, Long approverId, String rejectReason) {

        // 반려 사유 필수 검증
        if (rejectReason == null || rejectReason.isBlank()) {
            throw new CustomException(ErrorCode.REJECT_REASON_REQUIRED);
        }

        ApprovalRequest approvalRequest = findApprovalRequestById(approvalRequestId);
        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 자가 승인 차단
        validateSelfApproval(approver, approvalRequest);

        // SALES_MANAGER는 자신의 부서 영업사원 견적만 반려 가능 (관리자→관리자는 부서 무관)
        validateDepartmentIfManager(approver, approvalRequest);

        // PENDING 상태만 반려 가능
        validatePendingStatus(approvalRequest);

        ApprovalRequest.ApprovalStatus beforeStatus = approvalRequest.getStatus();

        // 반려 처리
        approvalRequest.reject(approver, rejectReason);
        approvalRequestRepository.save(approvalRequest);

        //[견적 파트 오케스트레이션 연동]: 반려된 견적서를 REVISING(수정 중) 상태로 변경하여 영업사원 수정 허용
        Quote quote = quoteRepository.findById(approvalRequest.getQuote().getId())
                .orElseThrow(() -> new CustomException(ErrorCode.QUOTE_NOT_FOUND));

        quote.startRevising(); //변경 감지 메서드 호출

        // 반려 이력 저장
        saveHistory(
                approvalRequest,
                approver,
                QuoteApprovalHistory.ActionType.REJECTED,
                beforeStatus,
                ApprovalRequest.ApprovalStatus.REJECTED,
                rejectReason
        );

        // 견적 작성자 통계 갱신 (반려 카운트 반영) - 커밋 이후 재집계
        userStatsUpdateService.recalculateAfterCommit(quote.getCreatedBy().getId());

        return approvalRequest;
    }

    // ── 4. 재요청 ──
    @Transactional
    public ApprovalRequest reRequest(Long approvalRequestId, Long requesterId, String requestMemo) {

        ApprovalRequest approvalRequest = findApprovalRequestById(approvalRequestId);
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // REJECTED 상태만 재요청 가능
        if (approvalRequest.getStatus() != ApprovalRequest.ApprovalStatus.REJECTED) {
            throw new CustomException(ErrorCode.APPROVAL_NOT_REJECTED);
        }

        // 본인 견적만 재요청 가능
        if (!approvalRequest.getRequester().getId().equals(requesterId)) {
            throw new CustomException(ErrorCode.APPROVAL_ACCESS_DENIED);
        }

        ApprovalRequest.ApprovalStatus beforeStatus = approvalRequest.getStatus();

        // 재요청 처리
        approvalRequest.reRequest(requestMemo);
        approvalRequestRepository.save(approvalRequest);

        // 재요청 이력 저장
        saveHistory(
                approvalRequest,
                requester,
                QuoteApprovalHistory.ActionType.RE_REQUESTED,
                beforeStatus,
                ApprovalRequest.ApprovalStatus.PENDING,
                requestMemo
        );

        return approvalRequest;
    }

    // ── 5. 승인 요청 메모 수정 ──
    @Transactional
    public void updateMemo(Long approvalRequestId, Long requesterId, String newMemo) {

        ApprovalRequest approvalRequest = findApprovalRequestById(approvalRequestId);

        // PENDING 상태만 수정 가능
        validatePendingStatus(approvalRequest);

        // 본인 요청건만 수정 가능
        if (!approvalRequest.getRequester().getId().equals(requesterId)) {
            throw new CustomException(ErrorCode.APPROVAL_ACCESS_DENIED);
        }

        approvalRequest.updateMemo(newMemo);
    }

    // ── 6. 이달 승인/반려 통계 ──
    public ApprovalMonthlyStatsResponse getMonthlyStats() {
        LocalDateTime from = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime to = from.plusMonths(1);

        long approved = approvalRequestRepository.countByStatusAndProcessedAtBetween(
                ApprovalRequest.ApprovalStatus.APPROVED, from, to);
        long rejected = approvalRequestRepository.countByStatusAndProcessedAtBetween(
                ApprovalRequest.ApprovalStatus.REJECTED, from, to);

        return new ApprovalMonthlyStatsResponse(approved, rejected);
    }

    // ── 7. 승인 대기 목록 조회 (SUPER_ADMIN - 전체) ──
    public List<ApprovalRequest> getPendingList() {
        return approvalRequestRepository.findByStatusOrderByRequestedAtAsc(
                ApprovalRequest.ApprovalStatus.PENDING
        );
    }

    // ── 7-1. 승인 대기 목록 조회 (SALES_MANAGER - 동일 부서 영업사원만) ──
    public List<ApprovalRequest> getPendingListForManager(Long managerId) {
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String department = manager.getDepartment();
        if (department == null || department.isBlank()) {
            return List.of();
        }

        return approvalRequestRepository.findByStatusAndRequesterDepartment(
                ApprovalRequest.ApprovalStatus.PENDING, department
        );
    }

    // ── 6. 승인 이력 조회 ──
    public List<QuoteApprovalHistory> getApprovalHistories(Long quoteId) {
        return quoteApprovalHistoryRepository.findAllByQuoteId(quoteId);
    }

    // ── 7. 승인 필요 사유 조회 ──
    public List<QuoteApprovalReason> getApprovalReasons(Long quoteId) {
        return quoteApprovalReasonRepository.findByQuote_Id(quoteId);
    }

    // ── Private 메서드 ──

    private ApprovalRequest findApprovalRequestById(Long approvalRequestId) {
        return approvalRequestRepository.findByIdWithUsers(approvalRequestId)
                .orElseThrow(() -> new CustomException(ErrorCode.APPROVAL_REQUEST_NOT_FOUND));
    }

    private void validatePendingStatus(ApprovalRequest approvalRequest) {
        if (approvalRequest.getStatus() != ApprovalRequest.ApprovalStatus.PENDING) {
            throw new CustomException(ErrorCode.APPROVAL_NOT_PENDING);
        }
    }

    private void validateSelfApproval(User approver, ApprovalRequest approvalRequest) {
        if (approver.getId().equals(approvalRequest.getRequester().getId())) {
            throw new CustomException(ErrorCode.APPROVAL_SELF_DENIED);
        }
    }

    private void validateDepartmentIfManager(User approver, ApprovalRequest approvalRequest) {
        if (approver.getRole() != UserRole.SALES_MANAGER) {
            return; // SUPER_ADMIN 등은 부서 제한 없음
        }
        // 요청자가 영업관리자면 부서 무관하게 다른 영업관리자가 처리 가능
        if (approvalRequest.getRequester().getRole() == UserRole.SALES_MANAGER) {
            return;
        }
        // 요청자가 영업사원이면 같은 부서 영업관리자만 처리 가능
        String approverDept = approver.getDepartment();
        String requesterDept = approvalRequest.getRequester().getDepartment();
        if (approverDept == null || !approverDept.equals(requesterDept)) {
            throw new CustomException(ErrorCode.APPROVAL_DEPT_MISMATCH);
        }
    }
    public ApprovalRequestDetailResponse getApprovalDetail(Long approvalRequestId) {

        ApprovalRequest approvalRequest = findApprovalRequestById(approvalRequestId);

        List<QuoteApprovalReason> reasons =
                quoteApprovalReasonRepository.findByQuote_Id(approvalRequest.getQuote().getId());

        List<QuoteApprovalHistory> histories =
                quoteApprovalHistoryRepository
                        .findByApprovalRequestIdOrderByActedAtAsc(approvalRequestId);

        return ApprovalRequestDetailResponse.from(approvalRequest, reasons, histories);
    }

    // ── 승인 상세 조회 (SALES_MANAGER - 동일 부서 영업사원만) ──
    public ApprovalRequestDetailResponse getApprovalDetailForManager(Long approvalRequestId, Long managerId) {

        ApprovalRequest approvalRequest = findApprovalRequestById(approvalRequestId);

        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String managerDept = manager.getDepartment();
        String requesterDept = approvalRequest.getRequester().getDepartment();

        if (managerDept == null || !managerDept.equals(requesterDept)) {
            throw new CustomException(ErrorCode.APPROVAL_DEPT_MISMATCH);
        }

        List<QuoteApprovalReason> reasons =
                quoteApprovalReasonRepository.findByQuote_Id(approvalRequest.getQuote().getId());

        List<QuoteApprovalHistory> histories =
                quoteApprovalHistoryRepository
                        .findByApprovalRequestIdOrderByActedAtAsc(approvalRequestId);

        return ApprovalRequestDetailResponse.from(approvalRequest, reasons, histories);
    }

    private void saveHistory(
            ApprovalRequest approvalRequest,
            User actor,
            QuoteApprovalHistory.ActionType action,
            ApprovalRequest.ApprovalStatus beforeStatus,
            ApprovalRequest.ApprovalStatus afterStatus,
            String memo
    ) {
        QuoteApprovalHistory history = QuoteApprovalHistory.of(
                approvalRequest,
                actor,
                action,
                beforeStatus,
                afterStatus,
                memo
        );
        quoteApprovalHistoryRepository.save(history);


    }

    @Transactional
    public void saveApprovalReasons(Quote quote, List<ApprovalReasonType> reasonTypes) {

        if (reasonTypes == null || reasonTypes.isEmpty()) {
            return;
        }

        List<QuoteApprovalReason> reasons = reasonTypes.stream()
                .map(type -> QuoteApprovalReason.of(
                        quote,
                        convertReasonType(type),
                        buildReasonMessage(type)
                ))
                .toList();

        quoteApprovalReasonRepository.saveAll(reasons);
    }

    // global enum → approval enum 변환
    private QuoteApprovalReason.ReasonType convertReasonType(ApprovalReasonType type) {
        return switch (type) {
            case DISCOUNT_EXCEEDED -> QuoteApprovalReason.ReasonType.DISCOUNT_EXCEEDED;
            case LOW_PROFIT -> QuoteApprovalReason.ReasonType.LOW_PROFIT;
            case HIGH_AMOUNT -> QuoteApprovalReason.ReasonType.HIGH_AMOUNT;
        };
    }

    private String buildReasonMessage(ApprovalReasonType type) {
        return switch (type) {
            case DISCOUNT_EXCEEDED -> "할인율이 정책 기준을 초과했습니다.";
            case LOW_PROFIT -> "이익률이 최소 기준에 미달합니다.";
            case HIGH_AMOUNT -> "견적 총액이 고액 기준 이상입니다.";
        };
    }

}