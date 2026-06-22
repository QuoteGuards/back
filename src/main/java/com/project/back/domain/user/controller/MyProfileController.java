package com.project.back.domain.user.controller;

import com.project.back.domain.user.dto.request.ChangeMyPasswordRequest;
import com.project.back.domain.user.dto.request.UpdateMyProfileRequest;
import com.project.back.domain.user.dto.response.MyProfileResponse;
import com.project.back.domain.user.service.MyProfileService;
import com.project.back.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class MyProfileController {

    private final MyProfileService myProfileService;

    /**
     * 내 프로필 수정 (이름, 전화번호)
     */
    @PatchMapping
    public ResponseEntity<ApiResponse<MyProfileResponse>> updateMyProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateMyProfileRequest request
    ) {
        MyProfileResponse result = myProfileService.updateMyProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success("프로필이 수정되었습니다.", result));
    }

    /**
     * 비밀번호 변경
     */
    @PatchMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changeMyPassword(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ChangeMyPasswordRequest request
    ) {
        myProfileService.changeMyPassword(userId, request);
        return ResponseEntity.ok(ApiResponse.success("비밀번호가 변경되었습니다.", null));
    }
}
