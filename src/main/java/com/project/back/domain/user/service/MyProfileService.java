package com.project.back.domain.user.service;

import com.project.back.domain.user.dto.request.ChangeMyPasswordRequest;
import com.project.back.domain.user.dto.request.UpdateMyProfileRequest;
import com.project.back.domain.user.dto.response.MyProfileResponse;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public MyProfileResponse updateMyProfile(Long userId, UpdateMyProfileRequest request) {
        User user = findUserById(userId);

        String phone = request.getPhone();
        if (phone != null && !phone.isBlank()) {
            if (userRepository.existsByPhoneAndIdNot(phone, userId)) {
                throw new CustomException(ErrorCode.DUPLICATE_PHONE);
            }
        }

        user.updateMyProfile(request.getName(), phone);
        return MyProfileResponse.from(user);
    }

    @Transactional
    public void changeMyPassword(Long userId, ChangeMyPasswordRequest request) {
        User user = findUserById(userId);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD);
        }

        if (!request.getNewPassword().equals(request.getNewPasswordConfirm())) {
            throw new CustomException(ErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.SAME_AS_CURRENT_PASSWORD);
        }

        user.changePassword(passwordEncoder.encode(request.getNewPassword()));
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
