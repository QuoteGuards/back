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
    CATEGORY_HAS_CHILDREN(HttpStatus.CONFLICT, "CAT_005", "하위 카테고리가 있어 삭제할 수 없습니다. 하위 카테고리를 먼저 삭제하세요."),

    // Product
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PROD_001", "제품을 찾을 수 없습니다."),
    DUPLICATE_PRODUCT_CODE(HttpStatus.CONFLICT, "PROD_002", "이미 사용 중인 제품 코드입니다."),
    PRODUCT_CATEGORY_INACTIVE(HttpStatus.CONFLICT, "PROD_003", "비활성 카테고리에는 제품을 등록하거나 활성화할 수 없습니다. 카테고리를 먼저 활성화하세요."),
    PRODUCT_IN_USE(HttpStatus.CONFLICT, "PROD_004", "견적서에 사용된 제품은 삭제할 수 없습니다. 비활성화를 사용하세요."),

    // Favorite
    FAVORITE_ALREADY_EXISTS(HttpStatus.CONFLICT, "FAV_001", "이미 즐겨찾기한 제품입니다."),
    FAVORITE_NOT_FOUND(HttpStatus.NOT_FOUND, "FAV_002", "즐겨찾기 내역을 찾을 수 없습니다."),

    // Quote
    QUOTE_NOT_FOUND(HttpStatus.NOT_FOUND, "QUOTE_001", "견적서를 찾을 수 없습니다."),
    QUOTE_NOT_EDITABLE(HttpStatus.BAD_REQUEST, "QUOTE_002", "수정 가능한 상태가 아닙니다."),
    QUOTE_NOT_EXPIRED(HttpStatus.BAD_REQUEST, "QUOTE_003", "만료된 견적만 재작성할 수 있습니다."),
    QUOTE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "QUOTE_004", "본인이 작성한 견적만 접근할 수 있습니다."),
    DISCOUNT_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "QUOTE_005", "할인율 변경 시 사유를 반드시 입력해야 합니다."),
    QUOTE_NOT_SENDABLE(HttpStatus.BAD_REQUEST, "QUOTE_006", "승인 완료 또는 승인 불필요 상태의 견적만 발송할 수 있습니다."),
    QUOTE_VALIDITY_EXPIRED(HttpStatus.BAD_REQUEST, "QUOTE_007", "견적 유효기간이 만료되어 발송할 수 없습니다. 만료 견적 재작성 및 재승인 후 발송해주세요."),
    QUOTE_EXPIRED_NOT_SENDABLE(HttpStatus.BAD_REQUEST, "QUOTE_008", "만료된 견적은 고객 발송할 수 없습니다. 만료 견적 재작성 후 승인을 다시 받아주세요."),
    QUOTE_VALID_UNTIL_REQUIRED(HttpStatus.BAD_REQUEST, "QUOTE_009", "견적 유효기간(만료일)을 입력해주세요."),
    QUOTE_VALID_UNTIL_INVALID(HttpStatus.BAD_REQUEST, "QUOTE_010", "견적 유효기간은 발행일 이후이며 오늘 이후 날짜여야 합니다."),
    QUOTE_NOT_YET_ISSUED(HttpStatus.BAD_REQUEST, "QUOTE_011", "견적 발행일 이전에는 발송할 수 없습니다."),
    QUOTE_NOT_CANCELLABLE(HttpStatus.BAD_REQUEST, "QUOTE_012", "발송 완료, 만료, 이미 취소된 견적은 취소할 수 없습니다."),

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

    // Initial Password Setup
    PASSWORD_NOT_INITIALIZED(HttpStatus.FORBIDDEN, "INIT_PWD_001", "초기 비밀번호 설정이 필요합니다. 이메일로 발송된 설정 링크를 확인해주세요."),
    INIT_PASSWORD_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "INIT_PWD_002", "유효하지 않은 비밀번호 설정 링크입니다."),
    INIT_PASSWORD_TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "INIT_PWD_003", "비밀번호 설정 링크가 만료되었습니다. 관리자에게 재발송을 요청해주세요."),
    INIT_PASSWORD_TOKEN_ALREADY_USED(HttpStatus.BAD_REQUEST, "INIT_PWD_004", "이미 사용된 비밀번호 설정 링크입니다."),
    INIT_PASSWORD_TOKEN_PURPOSE_MISMATCH(HttpStatus.BAD_REQUEST, "INIT_PWD_005", "올바르지 않은 링크 유형입니다."),
    INIT_PASSWORD_ALREADY_SET(HttpStatus.BAD_REQUEST, "INIT_PWD_006", "이미 비밀번호가 설정된 계정입니다."),
    INIT_PASSWORD_RESEND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "INIT_PWD_007", "잠시 후 다시 시도해주세요. (1분 대기)"),

    // Approval
    APPROVAL_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "APPROVAL_001", "승인 요청을 찾을 수 없습니다."),
    APPROVAL_ALREADY_PENDING(HttpStatus.CONFLICT, "APPROVAL_002", "이미 승인 대기 중인 요청이 있습니다."),
    APPROVAL_NOT_PENDING(HttpStatus.BAD_REQUEST, "APPROVAL_003", "승인 대기 상태의 요청만 처리할 수 있습니다."),
    APPROVAL_NOT_REJECTED(HttpStatus.BAD_REQUEST, "APPROVAL_004", "반려된 견적만 재요청할 수 있습니다."),
    APPROVAL_ACCESS_DENIED(HttpStatus.FORBIDDEN, "APPROVAL_005", "본인이 요청한 승인 건만 재요청할 수 있습니다."),
    REJECT_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "APPROVAL_006", "반려 사유는 필수입니다."),
    APPROVAL_DEPT_MISMATCH(HttpStatus.FORBIDDEN, "APPROVAL_007", "담당 부서의 승인 요청만 조회할 수 있습니다."),
    APPROVAL_SELF_DENIED(HttpStatus.FORBIDDEN, "APPROVAL_008", "자신이 요청한 견적은 직접 승인/반려할 수 없습니다."),
    AI_SUMMARY_GENERATION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "APPROVAL_009", "AI 리스크 요약 생성에 실패했습니다. 잠시 후 다시 시도해주세요."),
    APPROVAL_QUOTE_NOT_SUBMITTED(HttpStatus.BAD_REQUEST, "APPROVAL_010", "제출 완료된 견적만 승인 요청이 가능합니다."),
    AI_SUMMARY_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "APPROVAL_011", "AI 리스크 요약 호출 한도를 초과했습니다. 잠시 후 다시 시도해주세요."),
    APPROVAL_QUOTE_MISMATCH(HttpStatus.BAD_REQUEST, "APPROVAL_012", "요청 경로의 견적과 승인 요청의 견적이 일치하지 않습니다."),

    // Customer
    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "CUSTOMER_001", "존재하지 않는 고객입니다."),

    // Training
    TRAINING_CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "TRAINING_001", "교육 콘텐츠를 찾을 수 없습니다."),
    TRAINING_NOT_COMPLETED(HttpStatus.FORBIDDEN, "TRAINING_002", "필수 교육을 이수해야 견적을 작성할 수 있습니다."),

    // User Stats
    USER_STATS_NOT_FOUND(HttpStatus.NOT_FOUND, "STATS_001", "해당 사용자의 통계 데이터가 없습니다."),

    // File
    FILE_EMPTY(HttpStatus.BAD_REQUEST, "FILE_001", "업로드할 파일이 없습니다."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "FILE_002", "파일 크기가 너무 큽니다. (최대 5MB)"),
    FILE_INVALID_TYPE(HttpStatus.BAD_REQUEST, "FILE_003", "이미지 파일만 업로드할 수 있습니다."),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_004", "파일 업로드에 실패했습니다."),
    FILE_VIDEO_TOO_LARGE(HttpStatus.BAD_REQUEST, "FILE_005", "영상 파일 크기가 너무 큽니다. (최대 300MB)"),
    FILE_VIDEO_INVALID_TYPE(HttpStatus.BAD_REQUEST, "FILE_006", "MP4 영상 파일만 업로드할 수 있습니다."),

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "입력값이 유효하지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_999", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
