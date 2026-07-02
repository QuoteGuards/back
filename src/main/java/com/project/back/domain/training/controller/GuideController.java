package com.project.back.domain.training.controller;

import com.project.back.domain.training.dto.response.TrainingContentResponse;
import com.project.back.domain.training.service.TrainingService;
import com.project.back.domain.training.support.TrainingCoursePaths;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.global.common.ApiResponse;
import com.project.back.global.enums.TrainingType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/guides")
@RequiredArgsConstructor
public class GuideController {

    private final TrainingService trainingService;
    private final UserRepository userRepository;

    @GetMapping("/{courseKey}")
    @PreAuthorize("hasAnyRole('SALES_STAFF', 'SALES_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<TrainingContentResponse>> getGuide(
            @PathVariable String courseKey
    ) {
        TrainingType trainingType = TrainingCoursePaths.fromPathSegment(courseKey);
        return ResponseEntity.ok(
                ApiResponse.success(TrainingContentResponse.from(trainingService.getGuideContent(trainingType), List.of()))
        );
    }

    @PostMapping("/{courseKey}/confirm")
    @PreAuthorize("hasAnyRole('SALES_STAFF', 'SALES_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> confirmGuide(
            @AuthenticationPrincipal Long userId,
            @PathVariable String courseKey
    ) {
        TrainingType trainingType = TrainingCoursePaths.fromPathSegment(courseKey);
        trainingService.confirmGuide(getUser(userId), trainingType);
        return ResponseEntity.ok(ApiResponse.success("가이드 확인이 완료되었습니다.", null));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
    }
}
