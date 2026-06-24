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

    @Column(unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private boolean mustChangePassword = true;

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

    @Column(name = "suspended_by")
    private Long suspendedBy;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

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

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
        this.mustChangePassword = false;
    }

    public void changeRole(UserRole newRole) {
        this.role = newRole;
    }

    public void updateLastLoginAt() {
        this.lastLoginAt = LocalDateTime.now();
    }
}
