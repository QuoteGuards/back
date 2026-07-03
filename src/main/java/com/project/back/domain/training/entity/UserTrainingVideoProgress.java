package com.project.back.domain.training.entity;

import com.project.back.domain.user.entity.User;
import com.project.back.global.enums.TrainingStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_training_video_progress",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_training_video_progress_user_video",
                columnNames = {"user_id", "training_video_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserTrainingVideoProgress {

    private static final BigDecimal COMPLETE_THRESHOLD = new BigDecimal("80.00");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_video_id", nullable = false)
    private TrainingVideo trainingVideo;

    @Column(nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal progressRate = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TrainingStatus status = TrainingStatus.NOT_STARTED;

    @Column(nullable = false)
    @Builder.Default
    private int watchedSeconds = 0;

    @Column(nullable = false)
    @Builder.Default
    private int lastWatchedSeconds = 0;

    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public void updateProgress(BigDecimal progressRate, int watchedSeconds, int lastWatchedSeconds) {
        if (progressRate == null
                || progressRate.compareTo(BigDecimal.ZERO) < 0
                || progressRate.compareTo(new BigDecimal("100.00")) > 0) {
            throw new IllegalArgumentException("진도율은 0에서 100 사이여야 합니다.");
        }

        if (watchedSeconds < 0 || lastWatchedSeconds < 0 || lastWatchedSeconds > watchedSeconds) {
            throw new IllegalArgumentException("유효하지 않은 시청 시간입니다.");
        }

        this.progressRate = progressRate;
        this.watchedSeconds = watchedSeconds;
        this.lastWatchedSeconds = lastWatchedSeconds;

        if (this.status != TrainingStatus.COMPLETED) {
            if (this.progressRate.compareTo(COMPLETE_THRESHOLD) >= 0) {
                this.status = TrainingStatus.COMPLETED;
                this.completedAt = LocalDateTime.now();
            } else if (this.progressRate.compareTo(BigDecimal.ZERO) > 0) {
                this.status = TrainingStatus.IN_PROGRESS;
                this.completedAt = null;
            } else {
                this.status = TrainingStatus.NOT_STARTED;
                this.completedAt = null;
            }
        }
    }
}
