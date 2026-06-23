package com.project.back.domain.training.service;

import com.project.back.domain.training.entity.GuideConfirmation;
import com.project.back.domain.training.entity.TrainingContent;
import com.project.back.domain.training.entity.UserTrainingProgress;
import com.project.back.domain.training.repository.GuideConfirmationRepository;
import com.project.back.domain.training.repository.TrainingContentRepository;
import com.project.back.domain.training.repository.UserTrainingProgressRepository;
import com.project.back.domain.user.entity.User;
import com.project.back.global.enums.GuideType;
import com.project.back.global.enums.TrainingStatus;
import com.project.back.global.enums.TrainingType;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainingService {

    private final TrainingContentRepository trainingContentRepository;
    private final UserTrainingProgressRepository userTrainingProgressRepository;
    private final GuideConfirmationRepository guideConfirmationRepository;

    //견적 작성 필수 교육 콘텐츠 조회(활성화된 QUOTE_WRITE 타입 콘텐츠 반환)
    public TrainingContent getQuoteWritingContent() {
        return trainingContentRepository
                .findFirstByTrainingTypeAndActiveTrueOrderByUpdatedAtDesc(TrainingType.QUOTE_WRITE)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_CONTENT_NOT_FOUND));
    }

    //교육 영상 시청 진도율 저장 (이어보기 지원) (진도율 80% 이상 도달 시 자동으로 COMPLETED 전이)
    @Transactional
    public UserTrainingProgress updateProgress(User user,
                                               BigDecimal progressRate,
                                               int watchedSeconds,
                                               int lastWatchedSeconds) {
        TrainingContent content = getQuoteWritingContent();

        UserTrainingProgress progress = userTrainingProgressRepository
                .findByUserIdAndTrainingContentId(user.getId(), content.getId())
                .orElseGet(() -> createInitialProgress(user, content));

        progress.updateProgress(progressRate, watchedSeconds, lastWatchedSeconds);
        return progress;
    }

    //내 교육 이수 상태 조회
    public TrainingStatusResult getMyTrainingStatus(Long userId) {
        TrainingContent content = getQuoteWritingContent();

        UserTrainingProgress progress = userTrainingProgressRepository
                .findByUserIdAndTrainingContentId(userId, content.getId())
                .orElse(null);

        boolean guideConfirmed = guideConfirmationRepository
                .existsByUserIdAndGuideType(userId, GuideType.QUOTE_WRITE_GUIDE);

        return new TrainingStatusResult(progress, guideConfirmed);
    }

    //교육 이수 완료 여부 확인 (견적 작성 차단 판단용)
    public boolean isTrainingCompleted(Long userId) {
        TrainingContent content = getQuoteWritingContent();

        UserTrainingProgress progress = userTrainingProgressRepository
                .findByUserIdAndTrainingContentId(userId, content.getId())
                .orElse(null);

        boolean videoCompleted = (progress != null && progress.getStatus() == TrainingStatus.COMPLETED);

        boolean guideConfirmed = guideConfirmationRepository
                .existsByUserIdAndGuideType(userId, GuideType.QUOTE_WRITE_GUIDE);

        return videoCompleted && guideConfirmed;
    }

    //견적 작성 가이드 조회
    public TrainingContent getQuoteWritingGuide() {
        return getQuoteWritingContent();
    }

    //견적 작성 가이드 확인 완료 처리
    @Transactional
    public void confirmGuide(User user) {
        boolean alreadyConfirmed = guideConfirmationRepository
                .existsByUserIdAndGuideType(user.getId(), GuideType.QUOTE_WRITE_GUIDE);

        if (!alreadyConfirmed) {
            try {
                GuideConfirmation confirmation = GuideConfirmation.builder()
                        .user(user)
                        .guideType(GuideType.QUOTE_WRITE_GUIDE)
                        .build();
                guideConfirmationRepository.save(confirmation);
            } catch (DataIntegrityViolationException e) {
                // 이미 다른 요청에서 성공적으로 저장했으므로 아무것도 하지 않음 (멱등성 보장)
            }
        }
    }

    // ── 관리자: 이수 현황 조회 ────────────────────────

    //전체 사용자 교육 이수 현황 (SUPER_ADMIN용)
    public List<UserTrainingProgress> getAllTrainingStatus() {
        TrainingContent content = getQuoteWritingContent();
        return userTrainingProgressRepository
                .findAllByTrainingContentIdWithUser(content.getId());
    }

    private UserTrainingProgress createInitialProgress(User user, TrainingContent content) {
        try {
            UserTrainingProgress progress = UserTrainingProgress.builder()
                    .user(user)
                    .trainingContent(content)
                    .build();
            return userTrainingProgressRepository.save(progress);
        } catch (DataIntegrityViolationException e) {
            return userTrainingProgressRepository
                    .findByUserIdAndTrainingContentId(user.getId(), content.getId())
                    .orElseThrow(() -> e);
        }
    }

    //교육 이수 상태 조회 결과
    public record TrainingStatusResult(
            UserTrainingProgress progress,
            boolean guideConfirmed
    ) {
        public boolean isCompleted() {
            return progress != null
                    && progress.getStatus() == TrainingStatus.COMPLETED
                    && guideConfirmed;
        }
    }
}
