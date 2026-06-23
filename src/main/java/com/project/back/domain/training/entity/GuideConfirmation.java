package com.project.back.domain.training.entity;

import com.project.back.domain.user.entity.User;
import com.project.back.global.enums.GuideType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "guide_confirmations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_guide_confirmations_user_type",
                columnNames = {"user_id", "guide_type"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class GuideConfirmation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GuideType guideType;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime confirmedAt;
}
