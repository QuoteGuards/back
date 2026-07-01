package com.project.back.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_number", unique = true, nullable = false, length = 20)
    private String memberNumber;

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    /**
     * 사용자가 스스로 비밀번호를 설정했는지 여부.
     * false: 관리자가 생성했지만 아직 초기 비밀번호 설정 링크를 통해 설정하지 않음 → 로그인 불가
     * true: 사용자가 비밀번호를 설정 완료 → 로그인 가능
     */
    @Column(name = "password_initialized", nullable = false)
    @Builder.Default
    private boolean passwordInitialized = false;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 50)
    private String department;

    @Column(length = 50)
    private String position;

    @Column(unique = true, length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserRole role = UserRole.SALES_STAFF;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "suspended_by")
    private Long suspendedBy;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void suspend(Long suspenderId) {
        this.status = UserStatus.SUSPENDED;
        this.suspendedBy = suspenderId;
        this.suspendedAt = LocalDateTime.now();
    }

    public void reactivate() {
        this.status = UserStatus.ACTIVE;
        this.suspendedBy = null;
        this.suspendedAt = null;
    }

    public void delete() {
        this.status = UserStatus.DELETED;
        this.deletedAt = LocalDateTime.now();
    }

    public void updateInfo(String name, String phone, String department, String position) {
        if (name != null && !name.isBlank()) this.name = name;
        if (phone != null) this.phone = phone;
        if (department != null) this.department = department;
        if (position != null) this.position = position;
    }

    public void updateMyProfile(String name, String phone) {
        if (name != null && !name.isBlank()) this.name = name;
        if (phone != null) this.phone = phone.isBlank() ? null : phone;
    }

    /**
     * 초기 비밀번호 설정 완료: 사용자가 링크를 통해 비밀번호를 직접 설정한 경우 호출
     */
    public void setInitialPassword(String encodedPassword) {
        this.password = encodedPassword;
        this.passwordInitialized = true;
        this.passwordChangedAt = LocalDateTime.now();
    }

    /**
     * 비밀번호 변경 (이미 초기화된 사용자 대상)
     */
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
        this.passwordChangedAt = LocalDateTime.now();
    }

    public void changeRole(UserRole newRole) {
        this.role = newRole;
    }

    public void updateLastLoginAt() {
        this.lastLoginAt = LocalDateTime.now();
    }
}
