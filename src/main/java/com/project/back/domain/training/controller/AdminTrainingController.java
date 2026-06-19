package com.project.back.domain.training.controller;

import com.project.back.domain.training.dto.response.AdminTrainingStatusResponse;
import com.project.back.domain.training.service.TrainingService;
import com.project.back.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/trainings")
@RequiredArgsConstructor
public class AdminTrainingController {

    private final TrainingService trainingService;

    //전체 사용자 교육 이수 현황 조회 (SUPER_ADMIN 전용)
    @GetMapping("/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<AdminTrainingStatusResponse>>> getAllTrainingStatus() {
        List<AdminTrainingStatusResponse> result = trainingService.getAllTrainingStatus()
                .stream()
                .map(AdminTrainingStatusResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
