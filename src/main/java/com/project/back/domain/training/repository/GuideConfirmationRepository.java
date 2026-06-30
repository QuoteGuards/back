package com.project.back.domain.training.repository;

import com.project.back.domain.training.entity.GuideConfirmation;
import com.project.back.global.enums.GuideType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface GuideConfirmationRepository extends JpaRepository<GuideConfirmation, Long> {

    // 가이드 확인 완료 여부
    boolean existsByUserIdAndGuideType(Long userId, GuideType guideType);

    List<GuideConfirmation> findByGuideTypeAndUserIdIn(GuideType guideType, Collection<Long> userIds);
}
