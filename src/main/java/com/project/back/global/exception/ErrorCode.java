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

    // User Management
    USER_NOT_PENDING(HttpStatus.BAD_REQUEST, "USER_001", "승인 대기 중인 사용자만 승인/반려할 수 있습니다."),
    CANNOT_MODIFY_SELF(HttpStatus.BAD_REQUEST, "USER_002", "자기 자신의 권한 변경 또는 비활성화는 불가합니다."),
    USER_NOT_APPROVED(HttpStatus.BAD_REQUEST, "USER_003", "APPROVED 상태가 아닌 사용자는 비활성화할 수 없습니다."),
    USER_NOT_SUSPENDED(HttpStatus.BAD_REQUEST, "USER_004", "SUSPENDED 상태가 아닌 사용자는 재활성화할 수 없습니다."),

    // JWT
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "JWT_001", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "JWT_002", "만료된 토큰입니다."),

    // cateory
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "CAT_001", "카테고리를 찾을 수 없습니다."),
    CATEGORY_MAX_DEPTH_EXCEEDED(HttpStatus.BAD_REQUEST, "CAT_002", "카테고리는 최대 3단계까지만 등록 가능합니다."),
    CATEGORY_HAS_PRODUCTS(HttpStatus.CONFLICT, "CAT_003", "연결된 제품이 있어 삭제할 수 없습니다."),
    DUPLICATE_SLUG(HttpStatus.CONFLICT, "CAT_004", "이미 사용 중인 슬러그입니다."),

    // Product
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PROD_001", "제품을 찾을 수 없습니다."),
    DUPLICATE_PRODUCT_CODE(HttpStatus.CONFLICT, "PROD_002", "이미 사용 중인 제품 코드입니다."),

    // Favorite
    FAVORITE_ALREADY_EXISTS(HttpStatus.CONFLICT, "FAV_001", "이미 즐겨찾기한 제품입니다."),
    FAVORITE_NOT_FOUND(HttpStatus.NOT_FOUND, "FAV_002", "즐겨찾기 내역을 찾을 수 없습니다."),

    // Quote
    QUOTE_NOT_FOUND(HttpStatus.NOT_FOUND, "QUOTE_001", "견적서를 찾을 수 없습니다."),
    QUOTE_NOT_EDITABLE(HttpStatus.BAD_REQUEST, "QUOTE_002", "수정 가능한 상태가 아닙니다."),
    QUOTE_NOT_EXPIRED(HttpStatus.BAD_REQUEST, "QUOTE_003", "만료된 견적만 재작성할 수 있습니다."),
    QUOTE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "QUOTE_004", "본인이 작성한 견적만 접근할 수 있습니다."),

    // Discount Policy
    DISCOUNT_POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "DISCOUNT_001", "할인 정책을 찾을 수 없습니다."),
    DISCOUNT_TARGET_REQUIRED(HttpStatus.BAD_REQUEST, "DISCOUNT_002", "적용 대상(카테고리/제품)을 지정해야 합니다."),
    DISCOUNT_INVALID_PERIOD(HttpStatus.BAD_REQUEST, "DISCOUNT_003", "정책 종료일은 시작일보다 이후여야 합니다."),

    // Email
    EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "EMAIL_001", "이메일 발송에 실패했습니다."),

    // Customer
    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "CUSTOMER_001", "존재하지 않는 고객입니다."),

    // Training
    TRAINING_CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "TRAINING_001", "교육 콘텐츠를 찾을 수 없습니다."),
    TRAINING_NOT_COMPLETED(HttpStatus.FORBIDDEN, "TRAINING_002", "필수 교육을 이수해야 견적을 작성할 수 있습니다."),

    // User Stats
    USER_STATS_NOT_FOUND(HttpStatus.NOT_FOUND, "STATS_001", "해당 사용자의 통계 데이터가 없습니다."),

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "입력값이 유효하지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_999", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
