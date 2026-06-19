package com.project.back.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Auth
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "AUTH_001", "이미 사용 중인 이메일입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH_002", "존재하지 않는 사용자입니다."),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "AUTH_003", "비밀번호가 일치하지 않습니다."),
    USER_PENDING(HttpStatus.FORBIDDEN, "AUTH_004", "승인 대기 중인 계정입니다. 관리자 승인 후 이용 가능합니다."),
    USER_REJECTED(HttpStatus.FORBIDDEN, "AUTH_005", "거절된 계정입니다. 관리자에게 문의하세요."),
    USER_SUSPENDED(HttpStatus.FORBIDDEN, "AUTH_006", "정지된 계정입니다. 관리자에게 문의하세요."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH_007", "접근 권한이 없습니다."),

    // JWT
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "JWT_001", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "JWT_002", "만료된 토큰입니다."),

    // cateory
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "CAT_001", "카테고리를 찾을 수 없습니다."),
    CATEGORY_MAX_DEPTH_EXCEEDED(HttpStatus.BAD_REQUEST, "CAT_002", "카테고리는 최대 3단계까지만 등록 가능합니다."),
    CATEGORY_HAS_PRODUCTS(HttpStatus.CONFLICT, "CAT_003", "연결된 제품이 있어 삭제할 수 없습니다."),
    DUPLICATE_SLUG(HttpStatus.CONFLICT, "CAT_004", "이미 사용 중인 슬러그입니다."),

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "입력값이 유효하지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_999", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
