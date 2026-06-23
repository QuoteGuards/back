package com.project.back.domain.dashboard.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

// 기간 필터 변환: period 옵션 또는 사용자 지정(from/to) → [from, to] 구간
public record PeriodRange(LocalDateTime from, LocalDateTime to) {

    public static PeriodRange of(String period, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        return switch (period == null ? "" : period) {
            case "ONE_MONTH"    -> new PeriodRange(today.minusMonths(1).atStartOfDay(), null);
            case "THREE_MONTHS" -> new PeriodRange(today.minusMonths(3).atStartOfDay(), null);
            case "SIX_MONTHS"   -> new PeriodRange(today.minusMonths(6).atStartOfDay(), null);
            case "CUSTOM"       -> new PeriodRange(
                    from != null ? from.atStartOfDay() : null,
                    to   != null ? to.atTime(LocalTime.MAX) : null);
            default             -> new PeriodRange(null, null);  // 전체 기간
        };
    }
}
