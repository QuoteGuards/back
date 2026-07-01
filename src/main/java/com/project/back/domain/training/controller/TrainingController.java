package com.project.back.domain.training.controller;

import com.project.back.domain.training.dto.request.TrainingProgressRequest;
import com.project.back.domain.training.dto.response.TrainingContentResponse;
import com.project.back.domain.training.dto.response.TrainingStatusResponse;
import com.project.back.domain.training.service.TrainingService;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trainings")
@RequiredArgsConstructor
public class TrainingController {

    private final TrainingService trainingService;
    private final UserRepository userRepository;

    //견적 작성 필수 교육 조회
    @GetMapping("/quote-writing")
    @PreAuthorize("hasRole('SALES_STAFF')")
    public ResponseEntity<ApiResponse<TrainingContentResponse>> getQuoteWritingContent() {
        return ResponseEntity.ok(
                ApiResponse.success(TrainingContentResponse.from(trainingService.getQuoteWritingContent()))
        );
    }

    //교육 시청 진도율 저장
    @PatchMapping("/quote-writing/progress")
    @PreAuthorize("hasRole('SALES_STAFF')")
    public ResponseEntity<ApiResponse<Void>> updateProgress(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid TrainingProgressRequest request) {

        trainingService.updateProgress(
                getUser(userId),
                request.progressRate(),
                request.watchedSeconds(),
                request.lastWatchedSeconds()
        );
        return ResponseEntity.ok(ApiResponse.success("진도율이 저장되었습니다.", null));
    }

    //내 교육 이수 상태 조회
    @GetMapping("/me/status")
    @PreAuthorize("hasAnyRole('SALES_STAFF', 'SALES_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<TrainingStatusResponse>> getMyStatus(
            @AuthenticationPrincipal Long userId) {

        TrainingService.TrainingStatusResult result = trainingService.getMyTrainingStatus(getUser(userId));
        return ResponseEntity.ok(ApiResponse.success(TrainingStatusResponse.from(result)));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
    }
}