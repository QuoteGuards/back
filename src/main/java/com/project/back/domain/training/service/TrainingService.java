package com.project.back.domain.training.service;

import com.project.back.domain.training.dto.response.AdminTrainingStatusResponse;
import com.project.back.domain.training.entity.GuideConfirmation;
import com.project.back.domain.training.entity.TrainingContent;
import com.project.back.domain.training.entity.UserTrainingProgress;
import com.project.back.domain.training.repository.GuideConfirmationRepository;
import com.project.back.domain.training.repository.TrainingContentRepository;
import com.project.back.domain.training.repository.UserTrainingProgressRepository;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.entity.UserRole;
import com.project.back.domain.user.entity.UserStatus;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.global.enums.GuideType;
import com.project.back.global.enums.TrainingStatus;
import com.project.back.global.enums.TrainingType;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import com.project.back.global.storage.VideoFileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainingService {

    private final TrainingContentRepository trainingContentRepository;
    private final UserTrainingProgressRepository userTrainingProgressRepository;
    private final GuideConfirmationRepository guideConfirmationRepository;
    private final UserRepository userRepository;
    private final VideoFileStorage videoFileStorage;
    private final ObjectMapper objectMapper;

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
    public TrainingStatusResult getMyTrainingStatus(User user) {
        if (!isTrainingRequired(user)) {
            return TrainingStatusResult.notRequired();
        }

        TrainingContent content = getQuoteWritingContent();

        UserTrainingProgress progress = userTrainingProgressRepository
                .findByUserIdAndTrainingContentId(user.getId(), content.getId())
                .orElse(null);

        boolean guideConfirmed = guideConfirmationRepository
                .existsByUserIdAndGuideType(user.getId(), GuideType.QUOTE_WRITE_GUIDE);

        return new TrainingStatusResult(progress, guideConfirmed, true);
    }

    //교육 이수 완료 여부 확인 (견적 작성 차단 판단용 — 영업사원만 대상)
    public static boolean isTrainingRequired(User user) {
        return user != null && user.getRole() == UserRole.SALES_STAFF;
    }

    public boolean isTrainingCompleted(User user) {
        if (!isTrainingRequired(user)) {
            return true;
        }
        return isTrainingCompletedForStaff(user.getId());
    }

    private boolean isTrainingCompletedForStaff(Long userId) {
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

    private boolean isDuplicateConstraint(DataIntegrityViolationException e, String constraintName) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                return constraintName.equalsIgnoreCase(violation.getConstraintName());
            }
            cause = cause.getCause();
        }
        return false;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveGuideConfirmation(GuideConfirmation confirmation) {
        guideConfirmationRepository.saveAndFlush(confirmation);
    }

    //견적 작성 가이드 확인 완료 처리
    @Transactional
    public void confirmGuide(User user) {
        boolean alreadyConfirmed = guideConfirmationRepository
                .existsByUserIdAndGuideType(user.getId(), GuideType.QUOTE_WRITE_GUIDE);

        if (!alreadyConfirmed) {
            try {
                GuideConfirmation confirmation = GuideConfirmation.builder().user(user).guideType(GuideType.QUOTE_WRITE_GUIDE).build();
                saveGuideConfirmation(confirmation); // 격리된 트랜잭션 호출
            } catch (DataIntegrityViolationException e) {
                if (!isDuplicateConstraint(e, "uk_guide_confirmation_user_type")) throw e;
            }
        }
    }

    // ── 관리자: 교육 콘텐츠 · 이수 현황 ────────────────────────

    @Transactional
    public String uploadQuoteWritingVideo(MultipartFile file) {
        TrainingContent content = getQuoteWritingContent();
        String url = videoFileStorage.store(file, "trainings");
        content.updateVideoUrl(url);
        return url;
    }

    @Transactional
    public TrainingContent updateQuoteWritingGuideContent(String guideContent) {
        validateGuideContentJson(guideContent);
        TrainingContent content = getQuoteWritingContent();
        content.updateGuideContent(guideContent.trim());
        return content;
    }

    private void validateGuideContentJson(String guideContent) {
        try {
            objectMapper.readTree(guideContent);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    // 활성 영업사원 기준 교육 이수 현황 (미시작 포함)
    // SUPER_ADMIN: 전체, SALES_MANAGER: 동일 부서 영업사원만
    public List<AdminTrainingStatusResponse> getAdminTrainingStatusOverview(User requester) {
        if (requester.getRole() == UserRole.SALES_MANAGER) {
            if (requester.getDepartment() == null || requester.getDepartment().isBlank()) {
                throw new CustomException(ErrorCode.ACCESS_DENIED);
            }
        } else if (requester.getRole() != UserRole.SUPER_ADMIN) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        TrainingContent content = getQuoteWritingContent();

        List<User> salesUsers = new ArrayList<>();
        int page = 0;
        Page<User> salesUserPage;
        do {
            salesUserPage = userRepository
                    .findAllWithFilters(UserRole.SALES_STAFF, UserStatus.ACTIVE, null, PageRequest.of(page++, 1000));
            salesUsers.addAll(salesUserPage.getContent());
        } while (salesUserPage.hasNext());

        if (requester.getRole() == UserRole.SALES_MANAGER) {
            String managerDepartment = requester.getDepartment();
            salesUsers = salesUsers.stream()
                    .filter(user -> managerDepartment.equals(user.getDepartment()))
                    .toList();
        }

        List<UserTrainingProgress> progressList = userTrainingProgressRepository
                .findAllByTrainingContentIdWithUser(content.getId());
        Map<Long, UserTrainingProgress> progressByUserId = progressList.stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), p -> p, (left, right) -> left));

        List<Long> userIds = salesUsers.stream().map(User::getId).toList();
        Set<Long> guideConfirmedUserIds = userIds.isEmpty()
                ? Set.of()
                : guideConfirmationRepository.findByGuideTypeAndUserIdIn(GuideType.QUOTE_WRITE_GUIDE, userIds)
                .stream()
                .map(gc -> gc.getUser().getId())
                .collect(Collectors.toSet());

        return salesUsers.stream()
                .map(user -> AdminTrainingStatusResponse.from(
                        user,
                        content,
                        progressByUserId.get(user.getId()),
                        guideConfirmedUserIds.contains(user.getId())))
                .sorted(Comparator.comparing(AdminTrainingStatusResponse::userName))
                .toList();
    }

    // @deprecated getAdminTrainingStatusOverview() 사용
    public List<UserTrainingProgress> getAllTrainingStatus() {
        TrainingContent content = getQuoteWritingContent();
        return userTrainingProgressRepository
                .findAllByTrainingContentIdWithUser(content.getId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserTrainingProgress saveInitialProgress(User user, TrainingContent content) {
        UserTrainingProgress progress = UserTrainingProgress.builder()
                .user(user)
                .trainingContent(content)
                .build();
        return userTrainingProgressRepository.saveAndFlush(progress);
    }

    private UserTrainingProgress createInitialProgress(User user, TrainingContent content) {
        try {
            return saveInitialProgress(user, content);
        } catch (DataIntegrityViolationException e) {
            if (!isDuplicateConstraint(e, "uk_user_training_progress_user_content")) {
                throw e;
            }
            return userTrainingProgressRepository.findByUserIdAndTrainingContentId(user.getId(), content.getId())
                    .orElseThrow(() -> e);
        }
    }

    //교육 이수 상태 조회 결과
    public record TrainingStatusResult(
            UserTrainingProgress progress,
            boolean guideConfirmed,
            boolean trainingRequired
    ) {
        public static TrainingStatusResult notRequired() {
            return new TrainingStatusResult(null, true, false);
        }

        public boolean isCompleted() {
            if (!trainingRequired) {
                return true;
            }
            return progress != null
                    && progress.getStatus() == TrainingStatus.COMPLETED
                    && guideConfirmed;
        }
    }
}
