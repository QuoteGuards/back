package com.project.back.domain.training.config;

import com.project.back.domain.training.entity.TrainingContent;
import com.project.back.domain.training.repository.TrainingContentRepository;
import com.project.back.global.enums.TrainingType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class TrainingContentSeed {

    private static final String EMPTY_GUIDE = "{\"type\":\"doc\",\"content\":[]}";

    private final TrainingContentRepository trainingContentRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedMissingCourses() {
        seedIfMissing(
                TrainingType.QUOTE_WRITE,
                "견적 작성 필수 교육",
                "견적 생성을 위한 내부 가이드 및 시스템 사용법 교육입니다."
        );
        seedIfMissing(
                TrainingType.MANAGER_OPERATIONS,
                "영업 관리자 운영 교육",
                "승인 검토 절차, 부서 관리, 견적 운영 정책을 숙지하기 위한 교육입니다."
        );
    }

    private void seedIfMissing(TrainingType trainingType, String title, String description) {
        if (trainingContentRepository.findFirstByTrainingTypeAndActiveTrueOrderByUpdatedAtDesc(trainingType).isPresent()) {
            return;
        }

        try {
            trainingContentRepository.save(TrainingContent.builder()
                    .trainingType(trainingType)
                    .title(title)
                    .description(description)
                    .guideContent(EMPTY_GUIDE)
                    .required(true)
                    .active(true)
                    .build());
            log.info("Seeded training content for {}", trainingType);
        } catch (DataIntegrityViolationException e) {
            log.debug("Training content already seeded for {} (concurrent startup)", trainingType);
        }
    }
}
