package com.project.back.domain.training.service;

import com.project.back.domain.training.dto.response.AdminTrainingStatusResponse;
import com.project.back.domain.training.dto.response.TrainingContentResponse;
import com.project.back.domain.training.dto.response.TrainingVideoResponse;
import com.project.back.domain.training.entity.GuideConfirmation;
import com.project.back.domain.training.entity.TrainingContent;
import com.project.back.domain.training.entity.TrainingVideo;
import com.project.back.domain.training.entity.UserTrainingVideoProgress;
import com.project.back.domain.training.repository.GuideConfirmationRepository;
import com.project.back.domain.training.repository.TrainingContentRepository;
import com.project.back.domain.training.repository.TrainingVideoRepository;
import com.project.back.domain.training.repository.UserTrainingVideoProgressRepository;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainingService {

    private static final Logger log = LoggerFactory.getLogger(TrainingService.class);
    private static final BigDecimal COMPLETE_THRESHOLD = new BigDecimal("80.00");

    private final TrainingContentRepository trainingContentRepository;
    private final TrainingVideoRepository trainingVideoRepository;
    private final UserTrainingVideoProgressRepository userTrainingVideoProgressRepository;
    private final GuideConfirmationRepository guideConfirmationRepository;
    private final UserRepository userRepository;
    private final VideoFileStorage videoFileStorage;
    private final ObjectMapper objectMapper;

    public TrainingContent getQuoteWritingContent() {
        return trainingContentRepository
                .findFirstByTrainingTypeAndActiveTrueOrderByUpdatedAtDesc(TrainingType.QUOTE_WRITE)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_CONTENT_NOT_FOUND));
    }

    public TrainingContentResponse getQuoteWritingContentForStaff(User user) {
        TrainingContent content = getQuoteWritingContent();
        List<TrainingVideo> activeVideos = getActiveVideos(content.getId());
        Map<Long, UserTrainingVideoProgress> progressByVideoId = getProgressMap(user.getId(), content.getId());

        List<TrainingVideoResponse> videos = activeVideos.stream()
                .map(video -> TrainingVideoResponse.forStaff(video, progressByVideoId.get(video.getId())))
                .toList();

        return TrainingContentResponse.from(content, videos);
    }

    public TrainingContentResponse getQuoteWritingContentForAdmin() {
        TrainingContent content = getQuoteWritingContent();
        List<TrainingVideoResponse> videos = trainingVideoRepository
                .findByTrainingContentIdOrderBySortOrderAscIdAsc(content.getId())
                .stream()
                .map(TrainingVideoResponse::forAdmin)
                .toList();

        return TrainingContentResponse.from(content, videos);
    }

    @Transactional
    public UserTrainingVideoProgress updateVideoProgress(User user,
                                                         Long videoId,
                                                         BigDecimal progressRate,
                                                         int watchedSeconds,
                                                         int lastWatchedSeconds) {
        TrainingContent content = getQuoteWritingContent();
        TrainingVideo video = trainingVideoRepository.findByIdAndTrainingContentId(videoId, content.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_CONTENT_NOT_FOUND));

        if (!video.isActive()) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        UserTrainingVideoProgress progress = userTrainingVideoProgressRepository
                .findByUserIdAndTrainingVideoId(user.getId(), videoId)
                .orElseGet(() -> createInitialVideoProgress(user, video));

        progress.updateProgress(progressRate, watchedSeconds, lastWatchedSeconds);
        return progress;
    }

    public TrainingStatusResult getMyTrainingStatus(User user) {
        if (!isTrainingRequired(user)) {
            return TrainingStatusResult.notRequired();
        }

        TrainingContent content = getQuoteWritingContent();
        boolean guideConfirmed = guideConfirmationRepository
                .existsByUserIdAndGuideType(user.getId(), GuideType.QUOTE_WRITE_GUIDE);

        return buildStatusResult(user.getId(), content.getId(), guideConfirmed);
    }

    public static boolean isTrainingRequired(User user) {
        return user != null && user.getRole() == UserRole.SALES_STAFF;
    }

    public boolean isTrainingCompleted(User user) {
        if (!isTrainingRequired(user)) {
            return true;
        }
        TrainingContent content = getQuoteWritingContent();
        boolean guideConfirmed = guideConfirmationRepository
                .existsByUserIdAndGuideType(user.getId(), GuideType.QUOTE_WRITE_GUIDE);
        return isVideoRequirementMet(user.getId(), content.getId()) && guideConfirmed;
    }

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
                saveGuideConfirmation(confirmation);
            } catch (DataIntegrityViolationException e) {
                if (!isDuplicateConstraint(e, "uk_guide_confirmation_user_type")) {
                    throw e;
                }
            }
        }
    }

    @Transactional
    public TrainingVideoResponse uploadQuoteWritingVideo(MultipartFile file, String title) {
        TrainingContent content = getQuoteWritingContent();
        String url = videoFileStorage.store(file, "trainings");

        List<TrainingVideo> existing = trainingVideoRepository
                .findByTrainingContentIdOrderBySortOrderAscIdAsc(content.getId());
        int nextSortOrder = existing.stream()
                .mapToInt(TrainingVideo::getSortOrder)
                .max()
                .orElse(0) + 1;

        String resolvedTitle = (title == null || title.isBlank())
                ? "교육 영상 " + nextSortOrder
                : title.trim();

        TrainingVideo saved = trainingVideoRepository.save(TrainingVideo.builder()
                .trainingContent(content)
                .title(resolvedTitle)
                .videoUrl(url)
                .sortOrder(nextSortOrder)
                .active(false)
                .build());

        return TrainingVideoResponse.forAdmin(saved);
    }

    @Transactional
    public TrainingVideoResponse updateQuoteWritingVideoActive(Long videoId, boolean active) {
        TrainingContent content = getQuoteWritingContent();
        TrainingVideo video = trainingVideoRepository.findByIdAndTrainingContentId(videoId, content.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_CONTENT_NOT_FOUND));

        video.setActive(active);
        return TrainingVideoResponse.forAdmin(video);
    }

    @Transactional
    public TrainingVideoResponse updateQuoteWritingVideoTitle(Long videoId, String title) {
        TrainingContent content = getQuoteWritingContent();
        TrainingVideo video = trainingVideoRepository.findByIdAndTrainingContentId(videoId, content.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_CONTENT_NOT_FOUND));

        video.updateTitle(title.trim());
        return TrainingVideoResponse.forAdmin(video);
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

    public List<AdminTrainingStatusResponse> getAdminTrainingStatusOverview(User requester) {
        if (requester.getRole() == UserRole.SALES_MANAGER) {
            if (requester.getDepartment() == null || requester.getDepartment().isBlank()) {
                throw new CustomException(ErrorCode.ACCESS_DENIED);
            }
        } else if (requester.getRole() != UserRole.SUPER_ADMIN) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        TrainingContent content = getQuoteWritingContent();
        List<TrainingVideo> activeVideos = getActiveVideos(content.getId());
        List<Long> activeVideoIds = activeVideos.stream().map(TrainingVideo::getId).toList();

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

        Map<Long, List<UserTrainingVideoProgress>> progressByUserId = activeVideoIds.isEmpty()
                ? Map.of()
                : userTrainingVideoProgressRepository.findAllByTrainingVideoIdIn(activeVideoIds).stream()
                .collect(Collectors.groupingBy(p -> p.getUser().getId()));

        List<Long> userIds = salesUsers.stream().map(User::getId).toList();
        Set<Long> guideConfirmedUserIds = userIds.isEmpty()
                ? Set.of()
                : guideConfirmationRepository.findByGuideTypeAndUserIdIn(GuideType.QUOTE_WRITE_GUIDE, userIds)
                .stream()
                .map(gc -> gc.getUser().getId())
                .collect(Collectors.toSet());

        return salesUsers.stream()
                .map(user -> {
                    TrainingStatusResult result = buildStatusResult(
                            user.getId(),
                            content.getId(),
                            guideConfirmedUserIds.contains(user.getId())
                    );
                    return AdminTrainingStatusResponse.from(user, content, result);
                })
                .sorted(Comparator.comparing(AdminTrainingStatusResponse::userName))
                .toList();
    }

    private List<TrainingVideo> getActiveVideos(Long contentId) {
        return trainingVideoRepository.findByTrainingContentIdAndActiveTrueOrderBySortOrderAscIdAsc(contentId);
    }

    private Map<Long, UserTrainingVideoProgress> getProgressMap(Long userId, Long contentId) {
        return userTrainingVideoProgressRepository.findAllByUserIdAndContentId(userId, contentId).stream()
                .collect(Collectors.toMap(p -> p.getTrainingVideo().getId(), Function.identity(), (left, right) -> left));
    }

    private boolean isVideoRequirementMet(Long userId, Long contentId) {
        List<TrainingVideo> activeVideos = getActiveVideos(contentId);
        if (activeVideos.isEmpty()) {
            return false;
        }

        Map<Long, UserTrainingVideoProgress> progressByVideoId = getProgressMap(userId, contentId);
        return activeVideos.stream().allMatch(video -> {
            UserTrainingVideoProgress progress = progressByVideoId.get(video.getId());
            return progress != null && progress.getProgressRate().compareTo(COMPLETE_THRESHOLD) >= 0;
        });
    }

    private TrainingStatusResult buildStatusResult(Long userId, Long contentId, boolean guideConfirmed) {
        List<TrainingVideo> activeVideos = getActiveVideos(contentId);
        Map<Long, UserTrainingVideoProgress> progressByVideoId = getProgressMap(userId, contentId);

        List<TrainingVideoResponse> videos = activeVideos.stream()
                .map(video -> TrainingVideoResponse.forStaff(video, progressByVideoId.get(video.getId())))
                .toList();

        int activeVideoCount = activeVideos.size();
        int completedVideoCount = (int) activeVideos.stream()
                .filter(video -> {
                    UserTrainingVideoProgress progress = progressByVideoId.get(video.getId());
                    return progress != null && progress.getProgressRate().compareTo(COMPLETE_THRESHOLD) >= 0;
                })
                .count();

        BigDecimal aggregateProgressRate = activeVideoCount == 0
                ? BigDecimal.ZERO
                : activeVideos.stream()
                .map(video -> progressByVideoId.get(video.getId()))
                .map(progress -> progress == null ? BigDecimal.ZERO : progress.getProgressRate())
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        int aggregateWatchedSeconds = activeVideos.stream()
                .mapToInt(video -> {
                    UserTrainingVideoProgress progress = progressByVideoId.get(video.getId());
                    return progress == null ? 0 : progress.getWatchedSeconds();
                })
                .sum();

        int aggregateLastWatchedSeconds = activeVideos.stream()
                .mapToInt(video -> {
                    UserTrainingVideoProgress progress = progressByVideoId.get(video.getId());
                    return progress == null ? 0 : progress.getLastWatchedSeconds();
                })
                .max()
                .orElse(0);

        TrainingStatus aggregateStatus;
        if (activeVideoCount == 0) {
            aggregateStatus = TrainingStatus.NOT_STARTED;
        } else if (completedVideoCount == activeVideoCount) {
            aggregateStatus = TrainingStatus.COMPLETED;
        } else if (completedVideoCount > 0 || activeVideos.stream().anyMatch(video -> {
            UserTrainingVideoProgress progress = progressByVideoId.get(video.getId());
            return progress != null && progress.getProgressRate().compareTo(BigDecimal.ZERO) > 0;
        })) {
            aggregateStatus = TrainingStatus.IN_PROGRESS;
        } else {
            aggregateStatus = TrainingStatus.NOT_STARTED;
        }

        boolean videoCompleted = completedVideoCount == activeVideoCount && activeVideoCount > 0;
        boolean completed = videoCompleted && guideConfirmed;
        boolean additionalTrainingRequired = !completed
                && activeVideoCount > 0
                && (guideConfirmed || completedVideoCount > 0);

        return new TrainingStatusResult(
                aggregateStatus,
                aggregateProgressRate,
                aggregateWatchedSeconds,
                aggregateLastWatchedSeconds,
                guideConfirmed,
                true,
                activeVideoCount,
                completedVideoCount,
                additionalTrainingRequired,
                videos,
                completed
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserTrainingVideoProgress saveInitialVideoProgress(User user, TrainingVideo video) {
        UserTrainingVideoProgress progress = UserTrainingVideoProgress.builder()
                .user(user)
                .trainingVideo(video)
                .build();
        return userTrainingVideoProgressRepository.saveAndFlush(progress);
    }

    private UserTrainingVideoProgress createInitialVideoProgress(User user, TrainingVideo video) {
        try {
            return saveInitialVideoProgress(user, video);
        } catch (DataIntegrityViolationException e) {
            if (!isDuplicateConstraint(e, "uk_user_training_video_progress_user_video")) {
                throw e;
            }
            return userTrainingVideoProgressRepository.findByUserIdAndTrainingVideoId(user.getId(), video.getId())
                    .orElseThrow(() -> e);
        }
    }

    public record TrainingStatusResult(
            TrainingStatus aggregateStatus,
            BigDecimal aggregateProgressRate,
            int aggregateWatchedSeconds,
            int aggregateLastWatchedSeconds,
            boolean guideConfirmed,
            boolean trainingRequired,
            int activeVideoCount,
            int completedVideoCount,
            boolean additionalTrainingRequired,
            List<TrainingVideoResponse> videos,
            boolean completed
    ) {
        public static TrainingStatusResult notRequired() {
            return new TrainingStatusResult(
                    TrainingStatus.COMPLETED,
                    new BigDecimal("100.00"),
                    0,
                    0,
                    true,
                    false,
                    0,
                    0,
                    false,
                    List.of(),
                    true
            );
        }

        public boolean isCompleted() {
            return completed;
        }
    }
}
