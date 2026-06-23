package com.project.back.domain.dashboard.dto;

import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

// 기간 필터 변환: period 옵션 또는 사용자 지정(from/to) → [from, to] 구간
public record PeriodRange(LocalDateTime from, LocalDateTime to) {

    public static PeriodRange of(String period, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        return switch (period == null ? "" : period) {
            case ""             -> new PeriodRange(null, null);  // 미지정 → 전체 기간
            case "ONE_MONTH"    -> new PeriodRange(today.minusMonths(1).atStartOfDay(), null);
            case "THREE_MONTHS" -> new PeriodRange(today.minusMonths(3).atStartOfDay(), null);
            case "SIX_MONTHS"   -> new PeriodRange(today.minusMonths(6).atStartOfDay(), null);
            case "CUSTOM"       -> {
                LocalDateTime fromDt = from != null ? from.atStartOfDay() : null;
                LocalDateTime toDt   = to   != null ? to.atTime(LocalTime.MAX) : null;
                // 역순 범위(from > to) 거부 → 400 (빈 결과 폴백 방지)
                if (fromDt != null && toDt != null && fromDt.isAfter(toDt)) {
                    throw new CustomException(ErrorCode.INVALID_INPUT);
                }
                yield new PeriodRange(fromDt, toDt);
            }
            // 유효하지 않은 period 값 → 400 (조용한 전체 기간 폴백 방지)
            default             -> throw new CustomException(ErrorCode.INVALID_INPUT);
        };
    }
}
