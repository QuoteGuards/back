package com.project.back.domain.training.controller;

import com.project.back.domain.training.dto.request.TrainingGuideContentUpdateRequest;
import com.project.back.domain.training.dto.response.AdminTrainingStatusResponse;
import com.project.back.domain.training.dto.response.TrainingContentResponse;
import com.project.back.domain.training.service.TrainingService;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.global.common.ApiResponse;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/trainings")
@RequiredArgsConstructor
public class AdminTrainingController {

    private final TrainingService trainingService;
    private final UserRepository userRepository;

    @GetMapping("/quote-writing")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<TrainingContentResponse>> getQuoteWritingContent() {
        return ResponseEntity.ok(ApiResponse.success(
                TrainingContentResponse.from(trainingService.getQuoteWritingContent())));
    }

    @PostMapping(value = "/quote-writing/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadQuoteWritingVideo(
            @RequestParam("file") MultipartFile file
    ) {
        String url = trainingService.uploadQuoteWritingVideo(file);
        return ResponseEntity.ok(ApiResponse.success("교육 영상 업로드 성공", Map.of("url", url)));
    }

    @PatchMapping("/quote-writing/guide")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<TrainingContentResponse>> updateQuoteWritingGuide(
            @RequestBody @Valid TrainingGuideContentUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "가이드 내용이 저장되었습니다.",
                TrainingContentResponse.from(
                        trainingService.updateQuoteWritingGuideContent(request.guideContent()))));
    }

    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SALES_MANAGER')")
    public ResponseEntity<ApiResponse<List<AdminTrainingStatusResponse>>> getAllTrainingStatus(
            @AuthenticationPrincipal Long userId
    ) {
        List<AdminTrainingStatusResponse> result = trainingService.getAdminTrainingStatusOverview(getUser(userId));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
