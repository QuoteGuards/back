package com.project.back.domain.training.controller;

import com.project.back.domain.training.dto.request.TrainingGuideContentUpdateRequest;
import com.project.back.domain.training.dto.request.TrainingVideoActiveUpdateRequest;
import com.project.back.domain.training.dto.request.TrainingVideoTitleUpdateRequest;
import com.project.back.domain.training.dto.response.AdminTrainingStatusResponse;
import com.project.back.domain.training.dto.response.TrainingContentResponse;
import com.project.back.domain.training.dto.response.TrainingVideoResponse;
import com.project.back.domain.training.service.TrainingService;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.global.common.ApiResponse;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/trainings")
@RequiredArgsConstructor
public class AdminTrainingController {

    private final TrainingService trainingService;
    private final UserRepository userRepository;

    @GetMapping("/quote-writing")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<TrainingContentResponse>> getQuoteWritingContent() {
        return ResponseEntity.ok(ApiResponse.success(trainingService.getQuoteWritingContentForAdmin()));
    }

    @PostMapping(value = "/quote-writing/videos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<TrainingVideoResponse>> uploadQuoteWritingVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title
    ) {
        TrainingVideoResponse video = trainingService.uploadQuoteWritingVideo(file, title);
        return ResponseEntity.ok(ApiResponse.success("교육 영상이 추가되었습니다. 활성화하면 사원 교육에 반영됩니다.", video));
    }

    @PatchMapping("/quote-writing/videos/{videoId}/active")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<TrainingVideoResponse>> updateQuoteWritingVideoActive(
            @PathVariable Long videoId,
            @RequestBody @Valid TrainingVideoActiveUpdateRequest request
    ) {
        TrainingVideoResponse video = trainingService.updateQuoteWritingVideoActive(videoId, request.active());
        return ResponseEntity.ok(ApiResponse.success("영상 활성 상태가 변경되었습니다.", video));
    }

    @PatchMapping("/quote-writing/videos/{videoId}/title")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<TrainingVideoResponse>> updateQuoteWritingVideoTitle(
            @PathVariable Long videoId,
            @RequestBody @Valid TrainingVideoTitleUpdateRequest request
    ) {
        TrainingVideoResponse video = trainingService.updateQuoteWritingVideoTitle(videoId, request.title());
        return ResponseEntity.ok(ApiResponse.success("영상 제목이 변경되었습니다.", video));
    }

    @PatchMapping("/quote-writing/guide")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<TrainingContentResponse>> updateQuoteWritingGuide(
            @RequestBody @Valid TrainingGuideContentUpdateRequest request
    ) {
        trainingService.updateQuoteWritingGuideContent(request.guideContent());
        return ResponseEntity.ok(ApiResponse.success(
                "가이드 내용이 저장되었습니다.",
                trainingService.getQuoteWritingContentForAdmin()));
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
