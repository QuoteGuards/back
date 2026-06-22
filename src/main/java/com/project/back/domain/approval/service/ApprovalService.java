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
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.global.enums.ApprovalReasonType;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            throw new IllegalStateException("이미 승인 대기 중인 요청이 있습니다.");
        }

        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));

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
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));

        // PENDING 상태만 승인 가능
        validatePendingStatus(approvalRequest);

        ApprovalRequest.ApprovalStatus beforeStatus = approvalRequest.getStatus();

        // 승인 처리
        approvalRequest.approve(approver, memo);
        approvalRequestRepository.save(approvalRequest);

        // [견적 파트 오케스트레이션 연동]: 연관된 견적서를 찾아 확정 상태로 동기화
        Quote quote = quoteRepository.findById(approvalRequest.getQuote().getId())
                .orElseThrow(() -> new CustomException(ErrorCode.QUOTE_NOT_FOUND));

        // 최종 승인이 났으므로 즉시 발송 가능한 상태 혹은 고객 발송 준비 상태로 전이합니다.
        quote.markAsSent();

        // 승인 이력 저장
        saveHistory(
                approvalRequest,
                approver,
                QuoteApprovalHistory.ActionType.APPROVED,
                beforeStatus,
                ApprovalRequest.ApprovalStatus.APPROVED,
                memo
        );

        return approvalRequest;
    }

    // ── 3. 반려 처리 ──
    @Transactional
    public ApprovalRequest reject(Long approvalRequestId, Long approverId, String rejectReason) {

        // 반려 사유 필수 검증
        if (rejectReason == null || rejectReason.isBlank()) {
            throw new IllegalArgumentException("반려 사유는 필수입니다.");
        }

        ApprovalRequest approvalRequest = findApprovalRequestById(approvalRequestId);
        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));

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

        return approvalRequest;
    }

    // ── 4. 재요청 ──
    @Transactional
    public ApprovalRequest reRequest(Long approvalRequestId, Long requesterId, String requestMemo) {

        ApprovalRequest approvalRequest = findApprovalRequestById(approvalRequestId);
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));

        // REJECTED 상태만 재요청 가능
        if (approvalRequest.getStatus() != ApprovalRequest.ApprovalStatus.REJECTED) {
            throw new IllegalStateException("반려된 견적만 재요청할 수 있습니다.");
        }

        // 본인 견적만 재요청 가능
        if (!approvalRequest.getRequester().getId().equals(requesterId)) {
            throw new IllegalStateException("본인이 요청한 견적만 재요청할 수 있습니다.");
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

    // ── 5. 승인 대기 목록 조회 (관리자용) ──
    public List<ApprovalRequest> getPendingList() {
        return approvalRequestRepository.findByStatusOrderByRequestedAtAsc(
                ApprovalRequest.ApprovalStatus.PENDING
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
                .orElseThrow(() -> new EntityNotFoundException("승인 요청을 찾을 수 없습니다."));
    }

    private void validatePendingStatus(ApprovalRequest approvalRequest) {
        if (approvalRequest.getStatus() != ApprovalRequest.ApprovalStatus.PENDING) {
            throw new IllegalStateException("승인 대기 상태의 요청만 처리할 수 있습니다.");
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