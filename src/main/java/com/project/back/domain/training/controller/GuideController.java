package com.project.back.domain.training.controller;

import com.project.back.domain.training.dto.response.TrainingContentResponse;
import com.project.back.domain.training.service.TrainingService;

import java.util.List;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/guides")
@RequiredArgsConstructor
public class GuideController {

    private final TrainingService trainingService;
    private final UserRepository userRepository;

    //견적 작성 가이드 조회
    @GetMapping("/quote-writing")
    @PreAuthorize("hasAnyRole('SALES_STAFF', 'SALES_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<TrainingContentResponse>> getQuoteWritingGuide() {
        return ResponseEntity.ok(
                ApiResponse.success(TrainingContentResponse.from(trainingService.getQuoteWritingGuide(), List.of()))
        );
    }

    //견적 작성 가이드 확인 완료 처리
    @PostMapping("/quote-writing/confirm")
    @PreAuthorize("hasRole('SALES_STAFF')")
    public ResponseEntity<ApiResponse<Void>> confirmGuide(
            @AuthenticationPrincipal Long userId) {

        trainingService.confirmGuide(getUser(userId));
        return ResponseEntity.ok(ApiResponse.success("가이드 확인이 완료되었습니다.", null));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
    }
}