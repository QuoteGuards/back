package com.project.back.domain.quote.entity;

import com.project.back.global.enums.QuoteStatus;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Quote 엔티티 단위 테스트")
class QuoteTest {

    @ParameterizedTest
    @DisplayName("취소 가능 상태에서는 cancel() 호출 시 CANCELLED로 전환된다")
    @EnumSource(value = QuoteStatus.class, names = {
            "DRAFT", "SUBMITTED", "APPROVAL_NOT_REQUIRED", "APPROVAL_PENDING",
            "APPROVED", "REJECTED", "REVISING"
    })
    void cancel_allowedStatus_transitionsToCancelled(QuoteStatus status) {
        Quote quote = Quote.builder().status(status).build();

        quote.cancel();

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.CANCELLED);
    }

    @ParameterizedTest
    @DisplayName("취소 불가 상태(SENT/EXPIRED/CANCELLED)에서는 cancel() 호출 시 예외가 발생한다")
    @EnumSource(value = QuoteStatus.class, names = {"SENT", "EXPIRED", "CANCELLED"})
    void cancel_notCancellableStatus_throwsException(QuoteStatus status) {
        Quote quote = Quote.builder().status(status).build();

        assertThatThrownBy(quote::cancel)
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.QUOTE_NOT_CANCELLABLE);

        assertThat(quote.getStatus()).isEqualTo(status);
    }
}
