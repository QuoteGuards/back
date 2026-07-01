package com.project.back.domain.user.entity;

public enum UserRole {
    SUPER_ADMIN("최고관리자"),
    SALES_MANAGER("영업관리자"),
    SALES_STAFF("영업사원");

    private final String label;

    UserRole(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
