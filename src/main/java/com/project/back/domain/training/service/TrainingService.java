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
import com.project.back.domain.training.support.TrainingCourseSupport;
import com.project.back.domain.training.support.TrainingTransactionHelper;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private final TrainingTransactionHelper trainingTransactionHelper;

    public TrainingContent getContent(TrainingType trainingType) {
        return trainingContentRepository
                .findFirstByTrainingTypeAndActiveTrueOrderByUpdatedAtDesc(trainingType)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_CONTENT_NOT_FOUND));
    }

    public TrainingContent getQuoteWritingContent() {
        return getContent(TrainingType.QUOTE_WRITE);
    }

    public TrainingContentResponse getContentForStaff(User user, TrainingType trainingType) {
        validateStaffCourseAccess(user, trainingType);
        TrainingContent content = getContent(trainingType);
        List<TrainingVideo> activeVideos = getActiveVideos(content.getId());
        Map<Long, UserTrainingVideoProgress> progressByVideoId = getProgressMap(user.getId(), content.getId());

        List<TrainingVideoResponse> videos = activeVideos.stream()
                .map(video -> TrainingVideoResponse.forStaff(video, progressByVideoId.get(video.getId())))
                .toList();

        return TrainingContentResponse.from(content, videos);
    }

    public TrainingContentResponse getQuoteWritingContentForStaff(User user) {
        return getContentForStaff(user, TrainingType.QUOTE_WRITE);
    }

    public TrainingContentResponse getContentForAdmin(TrainingType trainingType) {
        TrainingContent content = getContent(trainingType);
        List<TrainingVideoResponse> videos = trainingVideoRepository
                .findByTrainingContentIdOrderBySortOrderAscIdAsc(content.getId())
                .stream()
                .map(TrainingVideoResponse::forAdmin)
                .toList();

        return TrainingContentResponse.from(content, videos);
    }

    public TrainingContentResponse getQuoteWritingContentForAdmin() {
        return getContentForAdmin(TrainingType.QUOTE_WRITE);
    }

    @Transactional
    public UserTrainingVideoProgress updateVideoProgress(User user,
                                                         TrainingType trainingType,
                                                         Long videoId,
                                                         BigDecimal progressRate,
                                                         int watchedSeconds,
                                                         int lastWatchedSeconds) {
        validateStaffCourseAccess(user, trainingType);
        TrainingContent content = getContent(trainingType);
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

    public List<TrainingType> getRequiredCourses(User user) {
        if (user == null) {
            return List.of();
        }

        return switch (user.getRole()) {
            case SALES_STAFF -> List.of(TrainingType.QUOTE_WRITE);
            case SALES_MANAGER -> {
                List<TrainingType> courses = new ArrayList<>();
                courses.add(TrainingType.MANAGER_OPERATIONS);
                if (!isCourseCompleted(user, TrainingType.QUOTE_WRITE)) {
                    courses.add(TrainingType.QUOTE_WRITE);
                }
                yield List.copyOf(courses);
            }
            default -> List.of();
        };
    }

    /** 교육 이수 화면 탭·콘텐츠 조회용 (승인 게이트는 getRequiredCourses 유지) */
    public List<TrainingType> getDisplayCourses(User user) {
        if (user == null) {
            return List.of();
        }

        return switch (user.getRole()) {
            case SALES_STAFF -> List.of(TrainingType.QUOTE_WRITE);
            case SALES_MANAGER -> List.of(TrainingType.QUOTE_WRITE, TrainingType.MANAGER_OPERATIONS);
            default -> List.of();
        };
    }

    public boolean isCourseCompleted(User user, TrainingType trainingType) {
        if (user == null) {
            return false;
        }

        TrainingContent content = trainingContentRepository
                .findFirstByTrainingTypeAndActiveTrueOrderByUpdatedAtDesc(trainingType)
                .orElse(null);
        if (content == null) {
            return false;
        }

        GuideType guideType = TrainingCourseSupport.guideTypeFor(trainingType);
        boolean guideConfirmed = guideConfirmationRepository.existsByUserIdAndGuideType(user.getId(), guideType);
        return isVideoRequirementMet(user.getId(), content.getId()) && guideConfirmed;
    }

    public TrainingStatusResult getMyTrainingStatus(User user) {
        List<TrainingType> displayCourses = getDisplayCourses(user);
        if (displayCourses.isEmpty()) {
            return TrainingStatusResult.notRequired();
        }

        List<CourseTrainingStatusResult> courses = displayCourses.stream()
                .map(type -> buildCourseStatusResult(user.getId(), type))
                .toList();

        boolean canWriteQuote = switch (user.getRole()) {
            case SALES_STAFF, SALES_MANAGER -> isCourseCompleted(user, TrainingType.QUOTE_WRITE);
            default -> true;
        };

        boolean canReviewApproval = canReviewApproval(user);

        CourseTrainingStatusResult primary = courses.stream()
                .filter(course -> !course.completed())
                .findFirst()
                .orElse(courses.get(0));

        boolean allCompleted = courses.stream().allMatch(CourseTrainingStatusResult::completed);
        boolean additionalTrainingRequired = courses.stream()
                .anyMatch(CourseTrainingStatusResult::additionalTrainingRequired);

        return new TrainingStatusResult(
                primary.aggregateStatus(),
                primary.aggregateProgressRate(),
                primary.aggregateWatchedSeconds(),
                primary.aggregateLastWatchedSeconds(),
                primary.guideConfirmed(),
                true,
                primary.activeVideoCount(),
                primary.completedVideoCount(),
                additionalTrainingRequired,
                primary.videos(),
                allCompleted,
                canWriteQuote,
                canReviewApproval,
                courses
        );
    }

    public static boolean isTrainingRequired(User user) {
        return user != null && (user.getRole() == UserRole.SALES_STAFF || user.getRole() == UserRole.SALES_MANAGER);
    }

    public boolean isTrainingCompleted(User user) {
        if (user == null) {
            return false;
        }
        return switch (user.getRole()) {
            case SALES_STAFF, SALES_MANAGER -> isCourseCompleted(user, TrainingType.QUOTE_WRITE);
            default -> true;
        };
    }

    public boolean canReviewApproval(User user) {
        if (user == null) {
            return false;
        }
        if (user.getRole() == UserRole.SUPER_ADMIN) {
            return true;
        }
        if (user.getRole() != UserRole.SALES_MANAGER) {
            return false;
        }
        return getRequiredCourses(user).stream().allMatch(type -> isCourseCompleted(user, type));
    }

    public void validateStaffCourseAccess(User user, TrainingType trainingType) {
        if (user == null) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        if (user.getRole() == UserRole.SUPER_ADMIN) {
            return;
        }
        if (!getAccessibleCourses(user).contains(trainingType)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
    }

    private List<TrainingType> getAccessibleCourses(User user) {
        if (user.getRole() == UserRole.SALES_MANAGER) {
            return getDisplayCourses(user);
        }
        return getRequiredCourses(user);
    }

    public TrainingContent getGuideContent(User user, TrainingType trainingType) {
        validateStaffCourseAccess(user, trainingType);
        return getContent(trainingType);
    }

    public TrainingContent getGuideContent(TrainingType trainingType) {
        return getContent(trainingType);
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

    @Transactional
    public void confirmGuide(User user, TrainingType trainingType) {
        validateStaffCourseAccess(user, trainingType);
        GuideType guideType = TrainingCourseSupport.guideTypeFor(trainingType);
        boolean alreadyConfirmed = guideConfirmationRepository.existsByUserIdAndGuideType(user.getId(), guideType);

        if (!alreadyConfirmed) {
            try {
                GuideConfirmation confirmation = GuideConfirmation.builder()
                        .user(user)
                        .guideType(guideType)
                        .build();
                trainingTransactionHelper.saveGuideConfirmation(confirmation);
            } catch (DataIntegrityViolationException e) {
                if (!isDuplicateConstraint(e, "uk_guide_confirmations_user_type")) {
                    throw e;
                }
            }
        }
    }

    @Transactional
    public void confirmGuide(User user) {
        confirmGuide(user, TrainingType.QUOTE_WRITE);
    }

    @Transactional
    public TrainingVideoResponse uploadVideo(TrainingType trainingType, MultipartFile file, String title) {
        TrainingContent content = getContent(trainingType);
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
    public TrainingVideoResponse uploadQuoteWritingVideo(MultipartFile file, String title) {
        return uploadVideo(TrainingType.QUOTE_WRITE, file, title);
    }

    @Transactional
    public TrainingVideoResponse updateVideoActive(TrainingType trainingType, Long videoId, boolean active) {
        TrainingContent content = getContent(trainingType);
        TrainingVideo video = trainingVideoRepository.findByIdAndTrainingContentId(videoId, content.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_CONTENT_NOT_FOUND));

        video.setActive(active);
        return TrainingVideoResponse.forAdmin(video);
    }

    @Transactional
    public TrainingVideoResponse updateQuoteWritingVideoActive(Long videoId, boolean active) {
        return updateVideoActive(TrainingType.QUOTE_WRITE, videoId, active);
    }

    @Transactional
    public TrainingVideoResponse updateVideoTitle(TrainingType trainingType, Long videoId, String title) {
        TrainingContent content = getContent(trainingType);
        TrainingVideo video = trainingVideoRepository.findByIdAndTrainingContentId(videoId, content.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_CONTENT_NOT_FOUND));

        video.updateTitle(title.trim());
        return TrainingVideoResponse.forAdmin(video);
    }

    @Transactional
    public TrainingVideoResponse updateQuoteWritingVideoTitle(Long videoId, String title) {
        return updateVideoTitle(TrainingType.QUOTE_WRITE, videoId, title);
    }

    @Transactional
    public TrainingContent updateGuideContent(TrainingType trainingType, String guideContent) {
        validateGuideContentJson(guideContent);
        TrainingContent content = getContent(trainingType);
        content.updateGuideContent(guideContent.trim());
        return content;
    }

    @Transactional
    public TrainingContent updateQuoteWritingGuideContent(String guideContent) {
        return updateGuideContent(TrainingType.QUOTE_WRITE, guideContent);
    }

    private void validateGuideContentJson(String guideContent) {
        try {
            objectMapper.readTree(guideContent);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    public List<AdminTrainingStatusResponse> getAdminTrainingStatusOverview(User requester, TrainingType trainingType) {
        if (requester.getRole() == UserRole.SALES_MANAGER) {
            if (requester.getDepartment() == null || requester.getDepartment().isBlank()) {
                throw new CustomException(ErrorCode.ACCESS_DENIED);
            }
        } else if (requester.getRole() != UserRole.SUPER_ADMIN) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        TrainingContent content = getContent(trainingType);
        List<TrainingVideo> activeVideos = getActiveVideos(content.getId());
        List<Long> activeVideoIds = activeVideos.stream().map(TrainingVideo::getId).toList();

        UserRole targetRole = trainingType == TrainingType.MANAGER_OPERATIONS
                ? UserRole.SALES_MANAGER
                : UserRole.SALES_STAFF;

        List<User> targetUsers = new ArrayList<>();
        int page = 0;
        Page<User> userPage;
        do {
            userPage = userRepository
                    .findAllWithFilters(targetRole, UserStatus.ACTIVE, null, PageRequest.of(page++, 1000));
            targetUsers.addAll(userPage.getContent());
        } while (userPage.hasNext());

        if (requester.getRole() == UserRole.SALES_MANAGER) {
            String managerDepartment = requester.getDepartment();
            targetUsers = targetUsers.stream()
                    .filter(user -> managerDepartment.equals(user.getDepartment()))
                    .toList();
        }

        Map<Long, List<UserTrainingVideoProgress>> progressByUserId = activeVideoIds.isEmpty()
                ? Map.of()
                : userTrainingVideoProgressRepository.findAllByTrainingVideoIdIn(activeVideoIds).stream()
                .collect(Collectors.groupingBy(p -> p.getUser().getId()));

        GuideType guideType = TrainingCourseSupport.guideTypeFor(trainingType);
        List<Long> userIds = targetUsers.stream().map(User::getId).toList();
        List<GuideConfirmation> guideConfirmations = userIds.isEmpty()
                ? List.of()
                : guideConfirmationRepository.findByGuideTypeAndUserIdIn(guideType, userIds);
        Set<Long> guideConfirmedUserIds = guideConfirmations.stream()
                .map(gc -> gc.getUser().getId())
                .collect(Collectors.toSet());
        Map<Long, LocalDateTime> guideConfirmedAtByUserId = guideConfirmations.stream()
                .collect(Collectors.toMap(gc -> gc.getUser().getId(), GuideConfirmation::getConfirmedAt, (left, right) -> left));

        return targetUsers.stream()
                .map(user -> {
                    CourseTrainingStatusResult result = buildCourseStatusResult(
                            user.getId(),
                            trainingType,
                            guideConfirmedUserIds.contains(user.getId()),
                            guideConfirmedAtByUserId.get(user.getId()),
                            progressByUserId.getOrDefault(user.getId(), List.of()),
                            content,
                            activeVideos
                    );
                    return AdminTrainingStatusResponse.from(user, content, result);
                })
                .sorted(Comparator.comparing(AdminTrainingStatusResponse::userName))
                .toList();
    }

    public List<AdminTrainingStatusResponse> getAdminTrainingStatusOverview(User requester) {
        return getAdminTrainingStatusOverview(requester, TrainingType.QUOTE_WRITE);
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

    private CourseTrainingStatusResult buildCourseStatusResult(Long userId, TrainingType trainingType) {
        GuideType guideType = TrainingCourseSupport.guideTypeFor(trainingType);
        boolean guideConfirmed = guideConfirmationRepository.existsByUserIdAndGuideType(userId, guideType);
        LocalDateTime guideConfirmedAt = guideConfirmed
                ? guideConfirmationRepository.findByUserIdAndGuideType(userId, guideType)
                .map(GuideConfirmation::getConfirmedAt)
                .orElse(null)
                : null;
        return buildCourseStatusResult(userId, trainingType, guideConfirmed, guideConfirmedAt, null, null, null);
    }

    private CourseTrainingStatusResult buildCourseStatusResult(Long userId,
                                                               TrainingType trainingType,
                                                               boolean guideConfirmed,
                                                               LocalDateTime guideConfirmedAt,
                                                               List<UserTrainingVideoProgress> knownProgress,
                                                               TrainingContent content,
                                                               List<TrainingVideo> activeVideos) {
        TrainingContent resolvedContent = content != null ? content : getContent(trainingType);
        List<TrainingVideo> resolvedActiveVideos = activeVideos != null
                ? activeVideos
                : getActiveVideos(resolvedContent.getId());
        Map<Long, UserTrainingVideoProgress> progressByVideoId = knownProgress == null
                ? getProgressMap(userId, resolvedContent.getId())
                : knownProgress.stream()
                .collect(Collectors.toMap(p -> p.getTrainingVideo().getId(), Function.identity(), (left, right) -> left));

        List<TrainingVideoResponse> videos = resolvedActiveVideos.stream()
                .map(video -> TrainingVideoResponse.forStaff(video, progressByVideoId.get(video.getId())))
                .toList();

        int activeVideoCount = resolvedActiveVideos.size();
        int completedVideoCount = (int) resolvedActiveVideos.stream()
                .filter(video -> {
                    UserTrainingVideoProgress progress = progressByVideoId.get(video.getId());
                    return progress != null && progress.getProgressRate().compareTo(COMPLETE_THRESHOLD) >= 0;
                })
                .count();

        BigDecimal aggregateProgressRate = activeVideoCount == 0
                ? BigDecimal.ZERO
                : resolvedActiveVideos.stream()
                .map(video -> progressByVideoId.get(video.getId()))
                .map(progress -> progress == null ? BigDecimal.ZERO : progress.getProgressRate())
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        int aggregateWatchedSeconds = resolvedActiveVideos.stream()
                .mapToInt(video -> {
                    UserTrainingVideoProgress progress = progressByVideoId.get(video.getId());
                    return progress == null ? 0 : progress.getWatchedSeconds();
                })
                .sum();

        int aggregateLastWatchedSeconds = resolvedActiveVideos.stream()
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
        } else if (completedVideoCount > 0 || resolvedActiveVideos.stream().anyMatch(video -> {
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
        LocalDateTime completedAt = resolveCompletedAt(completed, resolvedActiveVideos, progressByVideoId, guideConfirmedAt);

        return new CourseTrainingStatusResult(
                trainingType,
                aggregateStatus,
                aggregateProgressRate,
                aggregateWatchedSeconds,
                aggregateLastWatchedSeconds,
                guideConfirmed,
                activeVideoCount,
                completedVideoCount,
                additionalTrainingRequired,
                videos,
                completed,
                completedAt
        );
    }

    private LocalDateTime resolveCompletedAt(boolean completed,
                                             List<TrainingVideo> activeVideos,
                                             Map<Long, UserTrainingVideoProgress> progressByVideoId,
                                             LocalDateTime guideConfirmedAt) {
        if (!completed) {
            return null;
        }

        Optional<LocalDateTime> latestVideoCompletedAt = activeVideos.stream()
                .map(video -> progressByVideoId.get(video.getId()))
                .filter(Objects::nonNull)
                .map(UserTrainingVideoProgress::getCompletedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo);

        if (latestVideoCompletedAt.isEmpty()) {
            return guideConfirmedAt;
        }
        if (guideConfirmedAt == null) {
            return latestVideoCompletedAt.get();
        }
        return latestVideoCompletedAt.get().isAfter(guideConfirmedAt)
                ? latestVideoCompletedAt.get()
                : guideConfirmedAt;
    }

    private UserTrainingVideoProgress createInitialVideoProgress(User user, TrainingVideo video) {
        try {
            return trainingTransactionHelper.saveInitialVideoProgress(user, video);
        } catch (DataIntegrityViolationException e) {
            if (!isDuplicateConstraint(e, "uk_user_training_video_progress_user_video")) {
                throw e;
            }
            return userTrainingVideoProgressRepository.findByUserIdAndTrainingVideoId(user.getId(), video.getId())
                    .orElseThrow(() -> e);
        }
    }

    public record CourseTrainingStatusResult(
            TrainingType trainingType,
            TrainingStatus aggregateStatus,
            BigDecimal aggregateProgressRate,
            int aggregateWatchedSeconds,
            int aggregateLastWatchedSeconds,
            boolean guideConfirmed,
            int activeVideoCount,
            int completedVideoCount,
            boolean additionalTrainingRequired,
            List<TrainingVideoResponse> videos,
            boolean completed,
            LocalDateTime completedAt
    ) {
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
            boolean completed,
            boolean canWriteQuote,
            boolean canReviewApproval,
            List<CourseTrainingStatusResult> courses
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
                    true,
                    true,
                    true,
                    List.of()
            );
        }

        public boolean isCompleted() {
            return completed;
        }
    }
}
