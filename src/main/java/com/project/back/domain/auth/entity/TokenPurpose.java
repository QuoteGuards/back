package com.project.back.domain.auth.entity;

public enum TokenPurpose {
    /** 비밀번호 찾기 (비밀번호 재설정) */
    PASSWORD_RESET,
    /** 관리자가 계정 생성 후 사용자가 최초 비밀번호를 설정하는 용도 */
    INITIAL_PASSWORD_SETUP
}
