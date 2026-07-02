package com.project.back.domain.training.support;

import com.project.back.domain.training.entity.GuideConfirmation;
import com.project.back.domain.training.entity.TrainingVideo;
import com.project.back.domain.training.entity.UserTrainingVideoProgress;
import com.project.back.domain.training.repository.GuideConfirmationRepository;
import com.project.back.domain.training.repository.UserTrainingVideoProgressRepository;
import com.project.back.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class TrainingTransactionHelper {

    private final GuideConfirmationRepository guideConfirmationRepository;
    private final UserTrainingVideoProgressRepository userTrainingVideoProgressRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveGuideConfirmation(GuideConfirmation confirmation) {
        guideConfirmationRepository.saveAndFlush(confirmation);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserTrainingVideoProgress saveInitialVideoProgress(User user, TrainingVideo video) {
        UserTrainingVideoProgress progress = UserTrainingVideoProgress.builder()
                .user(user)
                .trainingVideo(video)
                .build();
        return userTrainingVideoProgressRepository.saveAndFlush(progress);
    }
}
