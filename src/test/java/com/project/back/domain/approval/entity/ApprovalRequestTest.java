package com.project.back.domain.approval.entity;

import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ApprovalRequest 엔티티 단위 테스트")
class ApprovalRequestTest {

    @Test
    @DisplayName("PENDING 상태에서는 cancel() 호출 시 CANCELLED로 전환된다")
    void cancel_pending_transitionsToCancelled() {
        ApprovalRequest request = ApprovalRequest.builder()
                .status(ApprovalRequest.ApprovalStatus.PENDING)
                .build();

        request.cancel();

        assertThat(request.getStatus()).isEqualTo(ApprovalRequest.ApprovalStatus.CANCELLED);
        assertThat(request.getProcessedAt()).isNotNull();
    }

    @ParameterizedTest
    @DisplayName("PENDING이 아닌 상태(APPROVED/REJECTED/CANCELLED)에서는 cancel() 호출 시 예외가 발생한다")
    @EnumSource(value = ApprovalRequest.ApprovalStatus.class, names = {"APPROVED", "REJECTED", "CANCELLED"})
    void cancel_notPending_throwsException(ApprovalRequest.ApprovalStatus status) {
        ApprovalRequest request = ApprovalRequest.builder()
                .status(status)
                .build();

        assertThatThrownBy(request::cancel)
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPROVAL_NOT_PENDING);

        assertThat(request.getStatus()).isEqualTo(status);
    }
}
