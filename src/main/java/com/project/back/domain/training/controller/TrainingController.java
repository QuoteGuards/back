package com.project.back.domain.training.controller;



import com.project.back.domain.training.dto.request.TrainingProgressRequest;

import com.project.back.domain.training.dto.response.TrainingContentResponse;

import com.project.back.domain.training.dto.response.TrainingStatusResponse;

import com.project.back.domain.training.service.TrainingService;

import com.project.back.domain.training.support.TrainingCoursePaths;

import com.project.back.domain.user.entity.User;

import com.project.back.domain.user.repository.UserRepository;

import com.project.back.global.common.ApiResponse;

import com.project.back.global.enums.TrainingType;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.PatchMapping;

import org.springframework.web.bind.annotation.PathVariable;

import org.springframework.web.bind.annotation.RequestBody;

import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;



@RestController

@RequestMapping("/api/trainings")

@RequiredArgsConstructor

public class TrainingController {



    private final TrainingService trainingService;

    private final UserRepository userRepository;



    @GetMapping("/{courseKey}")

    @PreAuthorize("hasAnyRole('SALES_STAFF', 'SALES_MANAGER')")

    public ResponseEntity<ApiResponse<TrainingContentResponse>> getTrainingContent(

            @AuthenticationPrincipal Long userId,

            @PathVariable String courseKey

    ) {

        TrainingType trainingType = TrainingCoursePaths.fromPathSegment(courseKey);
        User user = getUser(userId);
        trainingService.validateStaffCourseAccess(user, trainingType);

        return ResponseEntity.ok(ApiResponse.success(
                trainingService.getContentForStaff(user, trainingType)));

    }



    @PatchMapping("/{courseKey}/videos/{videoId}/progress")

    @PreAuthorize("hasAnyRole('SALES_STAFF', 'SALES_MANAGER')")

    public ResponseEntity<ApiResponse<Void>> updateVideoProgress(

            @AuthenticationPrincipal Long userId,

            @PathVariable String courseKey,

            @PathVariable Long videoId,

            @RequestBody @Valid TrainingProgressRequest request

    ) {

        TrainingType trainingType = TrainingCoursePaths.fromPathSegment(courseKey);
        User user = getUser(userId);
        trainingService.validateStaffCourseAccess(user, trainingType);

        trainingService.updateVideoProgress(
                user,

                trainingType,

                videoId,

                request.progressRate(),

                request.watchedSeconds(),

                request.lastWatchedSeconds()

        );

        return ResponseEntity.ok(ApiResponse.success("진도율이 저장되었습니다.", null));

    }



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

