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
    USER_SUSPENDED(HttpStatus.FORBIDDEN, "AUTH_004", "정지된 계정입니다. 관리자에게 문의하세요."),
    USER_DELETED(HttpStatus.FORBIDDEN, "AUTH_005", "삭제된 계정입니다. 관리자에게 문의하세요."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH_006", "접근 권한이 없습니다."),
    MUST_CHANGE_PASSWORD(HttpStatus.FORBIDDEN, "AUTH_007", "초기 비밀번호를 변경해야 합니다."),

    // User Management
    CANNOT_MODIFY_SELF(HttpStatus.BAD_REQUEST, "USER_001", "자기 자신의 권한 변경 또는 비활성화는 불가합니다."),
    USER_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "USER_002", "ACTIVE 상태가 아닌 사용자는 비활성화할 수 없습니다."),
    USER_NOT_SUSPENDED(HttpStatus.BAD_REQUEST, "USER_003", "SUSPENDED 상태가 아닌 사용자는 재활성화할 수 없습니다."),
    DUPLICATE_PHONE(HttpStatus.CONFLICT, "USER_004", "이미 사용 중인 전화번호입니다."),
    SAME_AS_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "USER_005", "새 비밀번호는 현재 비밀번호와 달라야 합니다."),
    PASSWORD_CONFIRM_MISMATCH(HttpStatus.BAD_REQUEST, "USER_006", "새 비밀번호와 비밀번호 확인이 일치하지 않습니다."),
    DUPLICATE_MEMBER_NUMBER(HttpStatus.CONFLICT, "USER_007", "이미 사용 중인 회원번호입니다."),
    MEMBER_NUMBER_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "USER_008", "회원번호 생성에 실패했습니다. 잠시 후 다시 시도해주세요."),

    // JWT
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "JWT_001", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "JWT_002", "만료된 토큰입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "JWT_003", "존재하지 않는 리프레시 토큰입니다."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "JWT_004", "만료된 리프레시 토큰입니다. 다시 로그인해주세요."),

    // Category
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
    DISCOUNT_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "QUOTE_005", "할인율 변경 시 사유를 반드시 입력해야 합니다."),

    // Discount Policy
    DISCOUNT_POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "DISCOUNT_001", "할인 정책을 찾을 수 없습니다."),
    DISCOUNT_TARGET_REQUIRED(HttpStatus.BAD_REQUEST, "DISCOUNT_002", "적용 대상(카테고리/제품)을 지정해야 합니다."),
    DISCOUNT_INVALID_PERIOD(HttpStatus.BAD_REQUEST, "DISCOUNT_003", "정책 종료일은 시작일보다 이후여야 합니다."),

    // Email
    EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "EMAIL_001", "이메일 발송에 실패했습니다."),

    // Password Reset
    PASSWORD_RESET_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "PWD_RESET_001", "유효하지 않은 비밀번호 재설정 링크입니다."),
    PASSWORD_RESET_TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "PWD_RESET_002", "비밀번호 재설정 링크가 만료되었습니다."),
    PASSWORD_RESET_TOKEN_ALREADY_USED(HttpStatus.BAD_REQUEST, "PWD_RESET_003", "이미 사용된 비밀번호 재설정 링크입니다."),
    PASSWORD_RESET_EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PWD_RESET_004", "비밀번호 재설정 이메일 발송에 실패했습니다."),
    PASSWORD_RESET_TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "PWD_RESET_005", "잠시 후 다시 시도해주세요. (1분 대기)"),

    // Approval
    APPROVAL_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "APPROVAL_001", "승인 요청을 찾을 수 없습니다."),
    APPROVAL_ALREADY_PENDING(HttpStatus.CONFLICT, "APPROVAL_002", "이미 승인 대기 중인 요청이 있습니다."),
    APPROVAL_NOT_PENDING(HttpStatus.BAD_REQUEST, "APPROVAL_003", "승인 대기 상태의 요청만 처리할 수 있습니다."),
    APPROVAL_NOT_REJECTED(HttpStatus.BAD_REQUEST, "APPROVAL_004", "반려된 견적만 재요청할 수 있습니다."),
    APPROVAL_ACCESS_DENIED(HttpStatus.FORBIDDEN, "APPROVAL_005", "본인이 요청한 승인 건만 재요청할 수 있습니다."),
    REJECT_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "APPROVAL_006", "반려 사유는 필수입니다."),

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
