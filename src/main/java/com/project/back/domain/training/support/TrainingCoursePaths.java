package com.project.back.domain.training.support;

import com.project.back.global.enums.TrainingType;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;

public final class TrainingCoursePaths {

    private TrainingCoursePaths() {
    }

    public static TrainingType fromPathSegment(String segment) {
        if (segment == null || segment.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return switch (segment.trim()) {
            case "quote-writing" -> TrainingType.QUOTE_WRITE;
            case "manager-operations" -> TrainingType.MANAGER_OPERATIONS;
            default -> throw new CustomException(ErrorCode.INVALID_INPUT);
        };
    }

    public static String toPathSegment(TrainingType trainingType) {
        return switch (trainingType) {
            case QUOTE_WRITE -> "quote-writing";
            case MANAGER_OPERATIONS -> "manager-operations";
        };
    }
}
