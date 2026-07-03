package com.project.back.notification.entity;

public enum NotificationType {
    USER_CREATED,
    USER_SUSPENDED,
    USER_REACTIVATED,
    PASSWORD_RESET,
    ROLE_CHANGED,
    APPROVAL_REQUESTED,
    APPROVAL_SLA_BREACH,
    QUOTE_APPROVED,
    QUOTE_REJECTED,
    QUOTE_EXPIRING,
    EMAIL_SENT,
    EMAIL_FAILED,
    SYSTEM
}
