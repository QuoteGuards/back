package com.project.back.domain.approval.service;

import com.project.back.domain.approval.dto.QuoteSnapshotDto;
import com.project.back.domain.approval.dto.response.ApprovalRequestDetailResponse;
import com.project.back.domain.approval.dto.response.QuoteDiffResponse;
import com.project.back.domain.approval.entity.ApprovalRequest;
import com.project.back.domain.approval.entity.QuoteApprovalHistory;
import com.project.back.domain.approval.entity.QuoteApprovalReason;
import com.project.back.domain.approval.repository.ApprovalRequestRepository;
import com.project.back.domain.approval.repository.QuoteApprovalHistoryRepository;
import com.project.back.domain.approval.repository.QuoteApprovalReasonRepository;
import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.quote.entity.QuoteItem;
import com.project.back.domain.quote.repository.QuoteItemRepository;
import com.project.back.domain.quote.repository.QuoteRepository;
import com.project.back.domain.quote.service.ApprovalCheckService;
import com.project.back.domain.training.service.TrainingService;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.entity.UserRole;
import com.project.back.domain.user.entity.UserStatus;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.domain.user.service.UserStatsUpdateService;
import com.project.back.global.enums.ApprovalReasonType;
import com.project.back.global.enums.QuoteStatus;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import com.project.back.notification.entity.NotificationRelatedType;
import com.project.back.notification.entity.NotificationType;
import com.project.back.notification.event.NotificationCreateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import com.project.back.domain.approval.dto.response.ApprovalMonthlyStatsResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApprovalService {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final QuoteApprovalReasonRepository quoteApprovalReasonRepository;
    private final QuoteApprovalHistoryRepository quoteApprovalHistoryRepository;
    private final UserRepository userRepository;
    private final QuoteRepository quoteRepository;
    private final QuoteItemRepository quoteItemRepository;
    private final ApprovalCheckService approvalCheckService;
    private final UserStatsUpdateService userStatsUpdateService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final TrainingService trainingService;
    private final ObjectMapper objectMapper;

    // ── 1. 승인 요청 ──
    @Transactional
    public ApprovalRequest requestApproval(Long quoteId, Long requesterId, String requestMemo) {

        // 교육 이수 체크는 QuoteService의 작성/제출 단계(validateTrainingCompleted)에서 이미 강제됨.
        // 승인 요청은 APPROVAL_PENDING 상태(= 작성 단계를 통과한 견적)에만 실행되므로 중복 체크 불필요.

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

        // APPROVAL_PENDING 상태(제출 완료 + 승인 필요)인 견적만 승인 요청 가능
        if (quote.getStatus() != QuoteStatus.APPROVAL_PENDING) {
            throw new CustomException(ErrorCode.APPROVAL_QUOTE_NOT_SUBMITTED);
        }

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

        // 승인 권한자에게 알림 발송
        notifyApprovers(requester, quote, approvalRequest.getId());

        return approvalRequest;
    }

    /**
     * 승인 요청 대상자에게 알림을 발송한다.
     * - 요청자가 영업사원이면: 같은 부서 영업관리자 + 전체 최고관리자
     * - 요청자가 영업관리자면: 전체 최고관리자
     */
    private void notifyApprovers(User requester, Quote quote, Long approvalRequestId) {
        String title = "새 승인 요청";
        String message = requester.getName() + "님이 견적 " + quote.getQuoteNumber() + " 승인을 요청했습니다.";

        for (User approver : resolveApprovers(requester)) {
            eventPublisher.publishEvent(new NotificationCreateEvent(
                    approver.getId(),
                    NotificationType.APPROVAL_REQUESTED,
                    title,
                    message,
                    NotificationRelatedType.APPROVAL,
                    approvalRequestId));
        }
    }

    /**
     * 승인 권한자(담당자) 목록을 결정한다.
     * - 요청자가 SALES_STAFF: 같은 부서 SALES_MANAGER 전원 + 전체 SUPER_ADMIN
     * - 요청자가 SALES_MANAGER: 전체 SUPER_ADMIN만
     * - 요청자 본인은 결과에서 제외 (본인이 SUPER_ADMIN 등으로 포함될 수 있으므로)
     */
    private List<User> resolveApprovers(User requester) {
        List<User> approvers = new java.util.ArrayList<>(
                userRepository.findByRoleAndStatus(UserRole.SUPER_ADMIN, UserStatus.ACTIVE));

        if (requester.getRole() == UserRole.SALES_STAFF && requester.getDepartment() != null) {
            approvers.addAll(userRepository.findByRoleAndDepartmentAndStatus(
                    UserRole.SALES_MANAGER, requester.getDepartment(), UserStatus.ACTIVE));
        }

        approvers.removeIf(approver -> approver.getId().equals(requester.getId()));
        return approvers;
    }

    // ── SLA 초과 승인 요청 알림 (스케줄러에서 매일 호출) ──
    public void notifySlaBreaches(int slaDays) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(slaDays);
        List<ApprovalRequest> breaches = approvalRequestRepository.findPendingRequestedBefore(threshold);

        for (ApprovalRequest approvalRequest : breaches) {
            try {
                notifySlaBreach(approvalRequest);
            } catch (Exception e) {
                log.warn("SLA 초과 알림 생성 실패 - approvalRequestId={}", approvalRequest.getId(), e);
            }
        }
    }

    private void notifySlaBreach(ApprovalRequest approvalRequest) {
        User requester = approvalRequest.getRequester();
        Quote quote = approvalRequest.getQuote();
        long daysPending = ChronoUnit.DAYS.between(approvalRequest.getRequestedAt(), LocalDateTime.now());

        String title = "승인 대기 SLA 초과";
        String message = "견적 " + quote.getQuoteNumber() + " 승인 요청이 " + daysPending + "일째 대기 중입니다.";

        for (User approver : resolveApprovers(requester)) {
            eventPublisher.publishEvent(new NotificationCreateEvent(
                    approver.getId(),
                    NotificationType.APPROVAL_SLA_BREACH,
                    title,
                    message,
                    NotificationRelatedType.APPROVAL,
                    approvalRequest.getId()));
        }
    }

    // ── 2. 승인 처리 ──
    @Transactional
    public ApprovalRequest approve(Long quoteId, Long approvalRequestId, Long approverId, String memo) {

        ApprovalRequest approvalRequest = findApprovalRequestById(approvalRequestId);
        validateQuoteMatch(quoteId, approvalRequest);
        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 자가 승인 차단
        validateSelfApproval(approver, approvalRequest);

        // SALES_MANAGER는 자신의 부서 영업사원 견적만 승인 가능 (관리자→관리자는 부서 무관)
        validateDepartmentIfManager(approver, approvalRequest);

        // PENDING 상태만 승인 가능
        validatePendingStatus(approvalRequest);
        validateApproverTraining(approver);

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

        // 견적 작성자에게 승인 알림 (트랜잭션 커밋 후 발행)
        eventPublisher.publishEvent(new NotificationCreateEvent(
                quote.getCreatedBy().getId(),
                NotificationType.QUOTE_APPROVED,
                "견적 승인 완료",
                "견적 " + quote.getQuoteNumber() + " 이(가) 승인되었습니다.",
                NotificationRelatedType.QUOTE,
                quote.getId()));

        return approvalRequest;
    }

    // ── 3. 반려 처리 ──
    @Transactional
    public ApprovalRequest reject(Long quoteId, Long approvalRequestId, Long approverId, String rejectReason) {

        // 반려 사유 필수 검증
        if (rejectReason == null || rejectReason.isBlank()) {
            throw new CustomException(ErrorCode.REJECT_REASON_REQUIRED);
        }

        ApprovalRequest approvalRequest = findApprovalRequestById(approvalRequestId);
        validateQuoteMatch(quoteId, approvalRequest);
        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 자가 승인 차단
        validateSelfApproval(approver, approvalRequest);

        // SALES_MANAGER는 자신의 부서 영업사원 견적만 반려 가능 (관리자→관리자는 부서 무관)
        validateDepartmentIfManager(approver, approvalRequest);

        // PENDING 상태만 반려 가능
        validatePendingStatus(approvalRequest);
        validateApproverTraining(approver);

        ApprovalRequest.ApprovalStatus beforeStatus = approvalRequest.getStatus();

        // 반려 처리
        approvalRequest.reject(approver, rejectReason);
        approvalRequestRepository.save(approvalRequest);

        //[견적 파트 오케스트레이션 연동]: 반려된 견적서를 REVISING(수정 중) 상태로 변경하여 영업사원 수정 허용
        Quote quote = quoteRepository.findById(approvalRequest.getQuote().getId())
                .orElseThrow(() -> new CustomException(ErrorCode.QUOTE_NOT_FOUND));

        quote.startRevising(); //변경 감지 메서드 호출

        // 반려 시점 견적 스냅샷 (재요청 시 변경 내역 비교용)
        List<QuoteItem> items = quoteItemRepository.findByQuoteIdOrderBySortOrderAsc(quote.getId());
        String quoteSnapshot = captureSnapshot(quote, items);

        // 반려 이력 저장
        saveHistory(
                approvalRequest,
                approver,
                QuoteApprovalHistory.ActionType.REJECTED,
                beforeStatus,
                ApprovalRequest.ApprovalStatus.REJECTED,
                rejectReason,
                quoteSnapshot
        );

        // 견적 작성자 통계 갱신 (반려 카운트 반영) - 커밋 이후 재집계
        userStatsUpdateService.recalculateAfterCommit(quote.getCreatedBy().getId());

        // 견적 작성자에게 반려 알림 (사유 포함, 트랜잭션 커밋 후 발행)
        eventPublisher.publishEvent(new NotificationCreateEvent(
                quote.getCreatedBy().getId(),
                NotificationType.QUOTE_REJECTED,
                "견적 반려",
                "견적 " + quote.getQuoteNumber() + " 이(가) 반려되었습니다. 사유: " + rejectReason,
                NotificationRelatedType.QUOTE,
                quote.getId()));

        return approvalRequest;
    }

    // ── 4. 재요청 ──
    @Transactional
    public ApprovalRequest reRequest(Long quoteId, Long approvalRequestId, Long requesterId, String requestMemo) {

        ApprovalRequest approvalRequest = findApprovalRequestById(approvalRequestId);
        validateQuoteMatch(quoteId, approvalRequest);
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

        // 재요청 처리 (PENDING 전환, 반려사유/AI 요약 초기화, requestedAt 갱신)
        approvalRequest.reRequest(requestMemo);
        approvalRequestRepository.save(approvalRequest);

        //[견적 파트 오케스트레이션 연동]: REVISING이었던 견적을 다시 APPROVAL_PENDING으로 전이
        Quote quote = quoteRepository.findById(approvalRequest.getQuote().getId())
                .orElseThrow(() -> new CustomException(ErrorCode.QUOTE_NOT_FOUND));

        // 반려 후 수정된 내용 기준으로 승인 사유를 다시 계산해 갱신 (수정 전 사유가 그대로 남는 것 방지)
        List<QuoteItem> items = quoteItemRepository.findByQuoteIdOrderBySortOrderAsc(quote.getId());
        List<ApprovalReasonType> reasons = approvalCheckService.check(
                quote.getDiscountPolicy(), items, quote.getTotalAmount(), quote.getProfitRate());

        // findById만으로는 approvalReasons 컬렉션이 로드되지 않아 clear()만으로 DB 행이 삭제되지 않음 → UK 중복
        quoteApprovalReasonRepository.deleteByQuote_Id(quote.getId());
        quote.getApprovalReasons().clear();
        if (!reasons.isEmpty()) {
            List<QuoteApprovalReason> reasonEntities = reasons.stream()
                    .map(r -> QuoteApprovalReason.of(quote, r, r.getDefaultMessage()))
                    .toList();
            quoteApprovalReasonRepository.saveAll(reasonEntities);
        }

        quote.complete(true);

        // 재요청 시점 견적 스냅샷 (반려 시점과 비교할 변경 내역 계산용)
        String quoteSnapshot = captureSnapshot(quote, items);

        // 재요청 이력 저장
        saveHistory(
                approvalRequest,
                requester,
                QuoteApprovalHistory.ActionType.RE_REQUESTED,
                beforeStatus,
                ApprovalRequest.ApprovalStatus.PENDING,
                requestMemo,
                quoteSnapshot
        );

        // 승인 권한자에게 알림 발송
        notifyApprovers(requester, quote, approvalRequest.getId());

        return approvalRequest;
    }

    // ── 4-1. 승인 요청 철회 (요청자 본인만, PENDING 상태만) ──
    @Transactional
    public ApprovalRequest cancelRequest(Long quoteId, Long approvalRequestId, Long requesterId) {

        ApprovalRequest approvalRequest = findApprovalRequestById(approvalRequestId);
        validateQuoteMatch(quoteId, approvalRequest);

        if (!approvalRequest.getRequester().getId().equals(requesterId)) {
            throw new CustomException(ErrorCode.APPROVAL_ACCESS_DENIED);
        }

        User requester = approvalRequest.getRequester();

        // PENDING 상태만 철회 가능 (ApprovalRequest.cancel()이 검증)
        approvalRequest.cancel();
        approvalRequestRepository.save(approvalRequest);

        // [견적 파트 오케스트레이션 연동]: 승인 요청 없이 다시 편집할 수 있도록 DRAFT로 되돌림
        Quote quote = quoteRepository.findById(approvalRequest.getQuote().getId())
                .orElseThrow(() -> new CustomException(ErrorCode.QUOTE_NOT_FOUND));
        quote.saveAsDraft();

        // 철회 이력 저장
        saveHistory(
                approvalRequest,
                requester,
                QuoteApprovalHistory.ActionType.CANCELLED,
                ApprovalRequest.ApprovalStatus.PENDING,
                ApprovalRequest.ApprovalStatus.CANCELLED,
                null
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

    // ── 7. 승인 목록 조회 (SUPER_ADMIN - 전체) ──
    // status/from/to/approverId가 모두 비어있으면 기존 승인 대기 목록 조회와 동일하게 동작 (하위 호환)
    public List<ApprovalRequest> getPendingList(
            ApprovalRequest.ApprovalStatus status, LocalDateTime from, LocalDateTime to, Long approverId
    ) {
        if (isDefaultPendingQuery(status, from, to, approverId)) {
            return approvalRequestRepository.findByStatusOrderByRequestedAtAsc(
                    ApprovalRequest.ApprovalStatus.PENDING
            );
        }

        PeriodRange period = resolvePeriod(from, to);
        return approvalRequestRepository.search(status, null, approverId, period.from(), period.to());
    }

    // ── 7-1. 승인 목록 조회 (SALES_MANAGER - 동일 부서 영업사원만) ──
    public List<ApprovalRequest> getPendingListForManager(
            Long managerId, ApprovalRequest.ApprovalStatus status, LocalDateTime from, LocalDateTime to, Long approverId
    ) {
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String department = manager.getDepartment();
        if (department == null || department.isBlank()) {
            return List.of();
        }

        if (isDefaultPendingQuery(status, from, to, approverId)) {
            return approvalRequestRepository.findByStatusAndRequesterDepartment(
                    ApprovalRequest.ApprovalStatus.PENDING, department
            );
        }

        PeriodRange period = resolvePeriod(from, to);
        return approvalRequestRepository.search(status, department, approverId, period.from(), period.to());
    }

    private boolean isDefaultPendingQuery(
            ApprovalRequest.ApprovalStatus status, LocalDateTime from, LocalDateTime to, Long approverId
    ) {
        return status == ApprovalRequest.ApprovalStatus.PENDING
                && from == null && to == null && approverId == null;
    }

    // from/to가 둘 다 비어있으면 이번 달 범위를 기본값으로 사용
    private PeriodRange resolvePeriod(LocalDateTime from, LocalDateTime to) {
        if (from != null || to != null) {
            return new PeriodRange(from, to);
        }
        LocalDateTime defaultFrom = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        return new PeriodRange(defaultFrom, defaultFrom.plusMonths(1));
    }

    private record PeriodRange(LocalDateTime from, LocalDateTime to) {}

    // ── 6. 승인 이력 조회 (SUPER_ADMIN - 전체) ──
    public List<QuoteApprovalHistory> getApprovalHistories(Long quoteId) {
        return quoteApprovalHistoryRepository.findAllByQuoteId(quoteId);
    }

    // ── 6-1. 승인 이력 조회 (SALES_STAFF - 본인 견적만) ──
    public List<QuoteApprovalHistory> getApprovalHistoriesForStaff(Long quoteId, Long userId) {
        Quote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new CustomException(ErrorCode.QUOTE_NOT_FOUND));
        if (!quote.getCreatedBy().getId().equals(userId)) {
            throw new CustomException(ErrorCode.APPROVAL_ACCESS_DENIED);
        }
        return quoteApprovalHistoryRepository.findAllByQuoteId(quoteId);
    }

    // ── 6-2. 승인 이력 조회 (SALES_MANAGER - 동일 부서 영업사원만) ──
    public List<QuoteApprovalHistory> getApprovalHistoriesForManager(Long quoteId, Long managerId) {
        Quote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new CustomException(ErrorCode.QUOTE_NOT_FOUND));
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        String managerDept = manager.getDepartment();
        String creatorDept = quote.getCreatedBy().getDepartment();
        if (managerDept == null || !managerDept.equals(creatorDept)) {
            throw new CustomException(ErrorCode.APPROVAL_DEPT_MISMATCH);
        }
        return quoteApprovalHistoryRepository.findAllByQuoteId(quoteId);
    }

    // ── 7. 승인 필요 사유 조회 (SUPER_ADMIN - 전체) ──
    public List<QuoteApprovalReason> getApprovalReasons(Long quoteId) {
        return quoteApprovalReasonRepository.findByQuote_Id(quoteId);
    }

    // ── 7-1. 승인 필요 사유 조회 (SALES_STAFF - 본인 견적만) ──
    public List<QuoteApprovalReason> getApprovalReasonsForStaff(Long quoteId, Long userId) {
        Quote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new CustomException(ErrorCode.QUOTE_NOT_FOUND));
        if (!quote.getCreatedBy().getId().equals(userId)) {
            throw new CustomException(ErrorCode.APPROVAL_ACCESS_DENIED);
        }
        return quoteApprovalReasonRepository.findByQuote_Id(quoteId);
    }

    // ── 7-2. 승인 필요 사유 조회 (SALES_MANAGER - 동일 부서 영업사원만) ──
    public List<QuoteApprovalReason> getApprovalReasonsForManager(Long quoteId, Long managerId) {
        Quote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new CustomException(ErrorCode.QUOTE_NOT_FOUND));
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        String managerDept = manager.getDepartment();
        String creatorDept = quote.getCreatedBy().getDepartment();
        if (managerDept == null || !managerDept.equals(creatorDept)) {
            throw new CustomException(ErrorCode.APPROVAL_DEPT_MISMATCH);
        }
        return quoteApprovalReasonRepository.findByQuote_Id(quoteId);
    }

    // ── Private 메서드 ──

    private ApprovalRequest findApprovalRequestById(Long approvalRequestId) {
        return approvalRequestRepository.findByIdWithUsers(approvalRequestId)
                .orElseThrow(() -> new CustomException(ErrorCode.APPROVAL_REQUEST_NOT_FOUND));
    }

    private void validateApproverTraining(User approver) {
        if (!trainingService.canReviewApproval(approver)) {
            throw new CustomException(ErrorCode.TRAINING_APPROVAL_NOT_COMPLETED);
        }
    }

    private void validatePendingStatus(ApprovalRequest approvalRequest) {
        if (approvalRequest.getStatus() != ApprovalRequest.ApprovalStatus.PENDING) {
            throw new CustomException(ErrorCode.APPROVAL_NOT_PENDING);
        }
    }

    // 경로의 quoteId와 승인 요청이 실제로 가리키는 견적이 일치하는지 검증 (다른 견적 ID로 잘못 처리되는 것 방지)
    private void validateQuoteMatch(Long quoteId, ApprovalRequest approvalRequest) {
        if (!approvalRequest.getQuote().getId().equals(quoteId)) {
            throw new CustomException(ErrorCode.APPROVAL_QUOTE_MISMATCH);
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
        Quote quote = getQuoteWithItems(approvalRequest.getQuote().getId());

        List<QuoteApprovalReason> reasons =
                quoteApprovalReasonRepository.findByQuote_Id(approvalRequest.getQuote().getId());

        List<QuoteApprovalHistory> histories =
                quoteApprovalHistoryRepository
                        .findByApprovalRequestIdOrderByActedAtAsc(approvalRequestId);

        QuoteDiffResponse quoteDiff = computeQuoteDiff(histories);
        return ApprovalRequestDetailResponse.from(approvalRequest, quote, reasons, histories, quoteDiff);
    }

    // ── 승인 상세 조회 (SALES_STAFF - 본인 요청건만) ──
    public ApprovalRequestDetailResponse getApprovalDetailForStaff(Long approvalRequestId, Long requesterId) {

        ApprovalRequest approvalRequest = findApprovalRequestById(approvalRequestId);

        if (!approvalRequest.getRequester().getId().equals(requesterId)) {
            throw new CustomException(ErrorCode.APPROVAL_ACCESS_DENIED);
        }

        Quote quote = getQuoteWithItems(approvalRequest.getQuote().getId());

        List<QuoteApprovalReason> reasons =
                quoteApprovalReasonRepository.findByQuote_Id(approvalRequest.getQuote().getId());

        List<QuoteApprovalHistory> histories =
                quoteApprovalHistoryRepository
                        .findByApprovalRequestIdOrderByActedAtAsc(approvalRequestId);

        QuoteDiffResponse quoteDiff = computeQuoteDiff(histories);
        return ApprovalRequestDetailResponse.from(approvalRequest, quote, reasons, histories, quoteDiff);
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

        Quote quote = getQuoteWithItems(approvalRequest.getQuote().getId());

        List<QuoteApprovalReason> reasons =
                quoteApprovalReasonRepository.findByQuote_Id(approvalRequest.getQuote().getId());

        List<QuoteApprovalHistory> histories =
                quoteApprovalHistoryRepository
                        .findByApprovalRequestIdOrderByActedAtAsc(approvalRequestId);

        QuoteDiffResponse quoteDiff = computeQuoteDiff(histories);
        return ApprovalRequestDetailResponse.from(approvalRequest, quote, reasons, histories, quoteDiff);
    }

    private Quote getQuoteWithItems(Long quoteId) {
        return quoteRepository.findByIdWithDetails(quoteId)
                .orElseThrow(() -> new CustomException(ErrorCode.QUOTE_NOT_FOUND));
    }

    private void saveHistory(
            ApprovalRequest approvalRequest,
            User actor,
            QuoteApprovalHistory.ActionType action,
            ApprovalRequest.ApprovalStatus beforeStatus,
            ApprovalRequest.ApprovalStatus afterStatus,
            String memo
    ) {
        saveHistory(approvalRequest, actor, action, beforeStatus, afterStatus, memo, null);
    }

    private void saveHistory(
            ApprovalRequest approvalRequest,
            User actor,
            QuoteApprovalHistory.ActionType action,
            ApprovalRequest.ApprovalStatus beforeStatus,
            ApprovalRequest.ApprovalStatus afterStatus,
            String memo,
            String quoteSnapshot
    ) {
        QuoteApprovalHistory history = QuoteApprovalHistory.of(
                approvalRequest,
                actor,
                action,
                beforeStatus,
                afterStatus,
                memo,
                quoteSnapshot
        );
        quoteApprovalHistoryRepository.save(history);
    }

    // 반려/재요청 시점의 견적 상태를 JSON 스냅샷으로 직렬화한다.
    // 실패해도 반려/재요청 처리 자체는 계속돼야 하므로 예외를 삼키고 null을 반환한다.
    private String captureSnapshot(Quote quote, List<QuoteItem> items) {
        try {
            BigDecimal discountRate = (quote.getSubtotal() != null
                    && quote.getSubtotal().compareTo(BigDecimal.ZERO) > 0)
                    ? quote.getDiscountAmount()
                            .divide(quote.getSubtotal(), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            QuoteSnapshotDto snapshot = QuoteSnapshotDto.builder()
                    .totalAmount(quote.getTotalAmount())
                    .profitRate(quote.getProfitRate())
                    .discountRate(discountRate)
                    .items(items.stream()
                            .map(item -> QuoteSnapshotDto.ItemSnapshot.builder()
                                    .productId(item.getProductId())
                                    .productName(item.getProductName())
                                    .quantity(item.getQuantity())
                                    .unitPrice(item.getUnitPrice())
                                    .build())
                            .toList())
                    .build();

            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            log.warn("견적 스냅샷 생성 실패 - quoteId={}", quote.getId(), e);
            return null;
        }
    }

    // 재요청 시 변경 내역(diff)을 계산한다.
    // "가장 최근 REJECTED" 이력과 그 다음에 온 "RE_REQUESTED" 이력의 스냅샷을 비교한다.
    // 반려된 적이 없거나, 반려는 됐지만 아직 재요청 전이면 null을 반환한다.
    private QuoteDiffResponse computeQuoteDiff(List<QuoteApprovalHistory> histories) {
        int lastRejectedIndex = -1;
        for (int i = 0; i < histories.size(); i++) {
            if (histories.get(i).getAction() == QuoteApprovalHistory.ActionType.REJECTED) {
                lastRejectedIndex = i;
            }
        }
        if (lastRejectedIndex == -1) {
            return null;
        }

        QuoteApprovalHistory rejected = histories.get(lastRejectedIndex);
        QuoteApprovalHistory reRequested = null;
        for (int i = lastRejectedIndex + 1; i < histories.size(); i++) {
            if (histories.get(i).getAction() == QuoteApprovalHistory.ActionType.RE_REQUESTED) {
                reRequested = histories.get(i);
                break;
            }
        }
        if (reRequested == null) {
            return null;
        }

        return buildDiff(rejected, reRequested);
    }

    private QuoteDiffResponse buildDiff(QuoteApprovalHistory rejected, QuoteApprovalHistory reRequested) {
        if (rejected.getQuoteSnapshot() == null || reRequested.getQuoteSnapshot() == null) {
            return null;
        }
        try {
            QuoteSnapshotDto before = objectMapper.readValue(rejected.getQuoteSnapshot(), QuoteSnapshotDto.class);
            QuoteSnapshotDto after = objectMapper.readValue(reRequested.getQuoteSnapshot(), QuoteSnapshotDto.class);
            return QuoteDiffResponse.of(before, after);
        } catch (Exception e) {
            log.warn("견적 변경 내역(diff) 계산 실패 - approvalRequestId={}",
                    rejected.getApprovalRequest().getId(), e);
            return null;
        }
    }

}