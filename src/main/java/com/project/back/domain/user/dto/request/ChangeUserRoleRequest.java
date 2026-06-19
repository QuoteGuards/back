package com.project.back.domain.user.dto.request;

import com.project.back.domain.user.entity.UserRole;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChangeUserRoleRequest {

    @NotNull(message = "변경할 권한은 필수입니다.")
    private UserRole role;
}
