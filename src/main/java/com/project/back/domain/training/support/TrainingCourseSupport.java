package com.project.back.domain.training.support;

import com.project.back.global.enums.GuideType;
import com.project.back.global.enums.TrainingType;

public final class TrainingCourseSupport {

    private TrainingCourseSupport() {
    }

    public static GuideType guideTypeFor(TrainingType trainingType) {
        return switch (trainingType) {
            case QUOTE_WRITE -> GuideType.QUOTE_WRITE_GUIDE;
            case MANAGER_OPERATIONS -> GuideType.MANAGER_OPERATIONS_GUIDE;
        };
    }
}
