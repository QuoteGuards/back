-- ================================================================================================================================
-- QuoteGuard Database
-- 영업 견적 자동화 및 리스크 검토 기반의 승인 관리 시스템
-- ================================================================================================================================

CREATE DATABASE IF NOT EXISTS quoteguard;

SET FOREIGN_KEY_CHECKS = 0;

USE quoteguard;

DROP TABLE IF EXISTS guide_confirmations;
DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS email_sends;
DROP TABLE IF EXISTS quote_approval_histories;
DROP TABLE IF EXISTS approval_requests;
DROP TABLE IF EXISTS quote_approval_reasons;
DROP TABLE IF EXISTS quote_items;
DROP TABLE IF EXISTS quotes;
DROP TABLE IF EXISTS discount_policies;
DROP TABLE IF EXISTS customers;
DROP TABLE IF EXISTS product_favorites;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS categories;
DROP TABLE IF EXISTS user_stats;
DROP TABLE IF EXISTS refresh_tokens;
DROP TABLE IF EXISTS password_reset_tokens;
DROP TABLE IF EXISTS user_training_video_progress;
DROP TABLE IF EXISTS training_videos;
DROP TABLE IF EXISTS user_training_progress;
DROP TABLE IF EXISTS training_contents;
DROP TABLE IF EXISTS users;

SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '사용자 식별자',

    member_number VARCHAR(20) NOT NULL COMMENT '시스템 자동 생성 로그인용 회원번호. 중복 불가',
    email VARCHAR(100) NOT NULL COMMENT '시스템 자동 생성 로그인용 이메일({member_number}@{account.email-domain} 형식). 실제 인증(로그인 조회) 및 초기 비밀번호 설정·재설정 링크 발송에 사용',
    password VARCHAR(255) NOT NULL COMMENT 'BCrypt 등으로 암호화된 비밀번호 해시값',
    password_initialized BOOLEAN NOT NULL DEFAULT FALSE COMMENT '사용자가 초기 비밀번호 설정 링크 또는 재설정 절차를 통해 실제 비밀번호를 설정했는지 여부',
    password_changed_at DATETIME NULL COMMENT '마지막 비밀번호 변경 일시',

    name VARCHAR(50) NOT NULL COMMENT '사용자 이름',
    department VARCHAR(50) NULL COMMENT '사용자 부서명. 예: 영업1팀, 영업관리팀',
    position VARCHAR(50) NULL COMMENT '사용자 직급. 예: 사원, 대리, 과장',
    phone VARCHAR(20) NULL COMMENT '사용자 휴대폰 번호',

    role ENUM('SUPER_ADMIN', 'SALES_MANAGER', 'SALES_STAFF') NOT NULL DEFAULT 'SALES_STAFF' COMMENT '사용자 권한: SUPER_ADMIN=최고관리자, SALES_MANAGER=영업관리자, SALES_STAFF=영업사원',
    status ENUM('ACTIVE', 'SUSPENDED', 'DELETED') NOT NULL DEFAULT 'ACTIVE' COMMENT '계정 상태: ACTIVE=사용 가능, SUSPENDED=관리자 정지, DELETED=삭제 또는 퇴사 처리',

    created_by BIGINT NULL COMMENT '계정을 생성한 관리자 ID. users.id 참조',
    suspended_by BIGINT NULL COMMENT '계정을 정지 처리한 관리자 ID. users.id 참조',
    suspended_at DATETIME NULL COMMENT '계정 정지 일시',
    deleted_at DATETIME NULL COMMENT '계정 삭제 또는 퇴사 처리 일시',

    last_login_at DATETIME NULL COMMENT '마지막 로그인 성공 일시',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '사용자 생성 일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '사용자 정보 수정 일시',

    CONSTRAINT uk_users_member_number UNIQUE (member_number),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_phone UNIQUE (phone),

    CONSTRAINT fk_users_created_by
        FOREIGN KEY (created_by)
        REFERENCES users(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_users_suspended_by
        FOREIGN KEY (suspended_by)
        REFERENCES users(id)
        ON DELETE SET NULL
) COMMENT = '관리자가 생성한 사용자 계정, 권한, 로그인 상태를 관리하는 테이블';


CREATE TABLE password_reset_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '비밀번호 재설정 토큰 식별자',

    user_id BIGINT NOT NULL COMMENT '비밀번호 재설정을 요청한 사용자 ID',
    token_hash VARCHAR(64) NOT NULL COMMENT '원본 토큰의 SHA-256 해시값(64자). 원본 토큰은 DB에 저장하지 않음',
    purpose ENUM('PASSWORD_RESET', 'INITIAL_PASSWORD_SETUP') NOT NULL DEFAULT 'PASSWORD_RESET' COMMENT '토큰 목적: PASSWORD_RESET=비밀번호 찾기, INITIAL_PASSWORD_SETUP=관리자 생성 계정의 초기 비밀번호 설정',

    expires_at DATETIME NOT NULL COMMENT '토큰 만료 일시',
    used_at DATETIME NULL COMMENT '토큰 사용 완료 일시. NULL이면 미사용 상태',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '토큰 생성 일시',

    CONSTRAINT fk_password_reset_tokens_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
) COMMENT = '이메일 기반 비밀번호 재설정 및 초기 비밀번호 설정 요청 토큰을 저장하는 테이블';


CREATE TABLE refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT 'Refresh Token 식별자',

    user_id BIGINT NOT NULL COMMENT '토큰 소유 사용자 ID',
    token_hash VARCHAR(64) NOT NULL COMMENT '원본 Refresh Token을 SHA-256으로 해시 처리한 값(64자). 원본 토큰은 DB에 저장하지 않음',

    expiry_date DATETIME NOT NULL COMMENT '토큰 만료 일시',

    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT uk_refresh_tokens_user UNIQUE (user_id),

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
) COMMENT = 'JWT Refresh Token을 저장하는 테이블. 토큰 갱신 및 로그아웃 처리에 사용';


CREATE TABLE training_contents (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '교육 콘텐츠 ID',

    training_type ENUM('QUOTE_WRITE', 'MANAGER_OPERATIONS') NOT NULL COMMENT '교육 유형',
    title VARCHAR(100) NOT NULL COMMENT '교육 제목',
    description TEXT NULL COMMENT '교육 설명',
    video_url VARCHAR(500) NULL COMMENT '교육 영상 URL 또는 파일 경로',
    guide_content TEXT NULL COMMENT '견적 작성 절차, 할인율 기준, 승인 요청 조건, 작성 예시 등 가이드 내용',

    required BOOLEAN NOT NULL DEFAULT TRUE COMMENT '필수 교육 여부',
    active BOOLEAN NOT NULL DEFAULT TRUE COMMENT '교육 콘텐츠 사용 여부',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일'
) COMMENT = '교육 콘텐츠';


CREATE TABLE training_videos (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '교육 영상 ID',

    training_content_id BIGINT NOT NULL COMMENT '소속 교육 콘텐츠 ID',
    title VARCHAR(100) NOT NULL COMMENT '영상 제목',
    video_url VARCHAR(500) NOT NULL COMMENT '영상 URL 또는 파일 경로',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '노출 순서',
    active BOOLEAN NOT NULL DEFAULT FALSE COMMENT '활성 여부. true인 영상만 사원에게 노출되며 이수 판정에 포함',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',

    CONSTRAINT fk_training_videos_content
        FOREIGN KEY (training_content_id)
        REFERENCES training_contents(id)
        ON DELETE RESTRICT
) COMMENT = '교육 콘텐츠에 속한 영상 목록';


CREATE TABLE user_training_video_progress (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '사용자 영상 시청 진도 ID',

    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    training_video_id BIGINT NOT NULL COMMENT '교육 영상 ID',

    progress_rate DECIMAL(5, 2) NOT NULL DEFAULT 0.00 COMMENT '영상 시청률',
    status ENUM('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED') NOT NULL DEFAULT 'NOT_STARTED' COMMENT '영상 이수 상태',

    watched_seconds INT NOT NULL DEFAULT 0 COMMENT '시청한 영상 시간',
    last_watched_seconds INT NOT NULL DEFAULT 0 COMMENT '마지막 시청 위치. 이어보기 기능에 사용',

    completed_at DATETIME NULL COMMENT '영상 이수 완료 시간',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',

    CONSTRAINT uk_user_training_video_progress_user_video UNIQUE (user_id, training_video_id),

    CONSTRAINT fk_user_training_video_progress_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_user_training_video_progress_video
        FOREIGN KEY (training_video_id)
        REFERENCES training_videos(id)
        ON DELETE RESTRICT
) COMMENT = '사용자별 교육 영상 시청 진도';


CREATE TABLE user_training_progress (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '사용자 교육 이수 ID',

    user_id BIGINT NOT NULL COMMENT '교육을 이수하는 사용자 ID',
    training_content_id BIGINT NOT NULL COMMENT '교육 콘텐츠 ID',

    progress_rate DECIMAL(5, 2) NOT NULL DEFAULT 0.00 COMMENT '교육 영상 시청률',
    status ENUM('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED') NOT NULL DEFAULT 'NOT_STARTED' COMMENT '교육 이수 상태',

    watched_seconds INT NOT NULL DEFAULT 0 COMMENT '시청한 영상 시간',
    last_watched_seconds INT NOT NULL DEFAULT 0 COMMENT '마지막 시청 위치. 이어보기 기능에 사용',

    completed_at DATETIME NULL COMMENT '교육 이수 완료 시간',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',

    CONSTRAINT uk_user_training_progress_user_content UNIQUE (user_id, training_content_id),

    CONSTRAINT fk_user_training_progress_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_user_training_progress_content
        FOREIGN KEY (training_content_id)
        REFERENCES training_contents(id)
        ON DELETE RESTRICT
) COMMENT = '사용자 교육 이수 현황';



CREATE TABLE user_stats (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '사용자 통계 식별자',
    user_id BIGINT NOT NULL COMMENT '통계 대상 사용자 ID',

    version BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA 낙관적 락 버전. 동시 통계 갱신 시 충돌 감지에 사용',

    total_quotes INT NOT NULL DEFAULT 0 COMMENT '사용자가 작성한 전체 견적 수',
    approved_quotes INT NOT NULL DEFAULT 0 COMMENT '승인 완료된 견적 수',
    rejected_quotes INT NOT NULL DEFAULT 0 COMMENT '반려된 견적 수',
    sent_quotes INT NOT NULL DEFAULT 0 COMMENT '고객에게 발송 완료된 견적 수',

    total_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '사용자가 작성한 견적의 총 최종 금액 합계',
    total_supply_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '사용자의 총 VAT 제외 공급가액 합계',
    total_profit_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '사용자의 총 예상 이익금 합계',

    average_discount_rate DECIMAL(5, 2) NOT NULL DEFAULT 0.00 COMMENT '사용자의 평균 할인율',
    average_profit_rate DECIMAL(7, 2) NOT NULL DEFAULT 0.00 COMMENT '사용자의 평균 이익률',

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '통계 갱신 일시',

    CONSTRAINT uk_user_stats_user UNIQUE (user_id),

    CONSTRAINT fk_user_stats_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
) COMMENT = '사용자별 견적 작성 수, 승인/반려 수, 평균 할인율, 평균 이익률 등 대시보드 통계를 저장하는 테이블';


CREATE TABLE categories (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '카테고리 식별자',

    parent_id BIGINT NULL COMMENT '상위 카테고리 ID. NULL이면 대분류',
    name VARCHAR(100) NOT NULL COMMENT '카테고리명',
    slug VARCHAR(100) NOT NULL COMMENT 'URL 또는 검색에 사용할 카테고리 식별 문자열',
    depth TINYINT NOT NULL DEFAULT 1 COMMENT '카테고리 깊이. 1=대분류, 2=중분류, 3=소분류',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '카테고리 정렬 순서',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT '카테고리 사용 여부',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '카테고리 생성 일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '카테고리 수정 일시',

    CONSTRAINT uk_categories_slug UNIQUE (slug),

    CONSTRAINT fk_categories_parent
        FOREIGN KEY (parent_id)
        REFERENCES categories(id)
        ON DELETE CASCADE
) COMMENT = '제품 대분류, 중분류, 소분류를 계층형으로 관리하는 테이블';


CREATE TABLE products (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '제품 식별자',

    category_id BIGINT NOT NULL COMMENT '제품이 속한 카테고리 ID',

    name VARCHAR(255) NOT NULL COMMENT '제품명',
    code VARCHAR(100) NOT NULL COMMENT '제품 코드 또는 SKU. 중복 불가',
    description TEXT NULL COMMENT '제품 설명',
	spec VARCHAR(100) NULL COMMENT '제품 규격. 자율 형식으로 입력',
    image_url VARCHAR(500) NULL COMMENT '제품 이미지 URL 또는 저장 경로',
    unit_price DECIMAL(15, 2) NOT NULL DEFAULT 0.00 COMMENT '제품 판매 단가',
    cost_price DECIMAL(15, 2) NOT NULL DEFAULT 0.00 COMMENT '제품 원가. 내부 견적 분석의 이익금 계산에 사용',

    unit VARCHAR(20) NOT NULL DEFAULT 'EA' COMMENT '판매 단위. 예: EA, BOX, KG',
    vat_applicable BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'VAT 적용 여부',

    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT '제품 사용 여부. 비활성화 시 견적 작성에서 제외',
    view_count INT NOT NULL DEFAULT 0 COMMENT '제품 상세 조회 수. 인기 제품 통계에 활용',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '제품 생성 일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '제품 수정 일시',

    CONSTRAINT uk_products_code UNIQUE (code),

    CONSTRAINT fk_products_category
        FOREIGN KEY (category_id)
        REFERENCES categories(id)
        ON DELETE RESTRICT
) COMMENT = '제품명, 제품코드, 이미지, 단가, 원가, VAT 여부를 관리하는 테이블';


CREATE TABLE product_favorites (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '제품 즐겨찾기 식별자',

    user_id BIGINT NOT NULL COMMENT '즐겨찾기를 등록한 사용자 ID',
    product_id BIGINT NOT NULL COMMENT '즐겨찾기 등록된 제품 ID',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '즐겨찾기 등록 일시',

    CONSTRAINT uk_product_favorites_user_product UNIQUE (user_id, product_id),

    CONSTRAINT fk_product_favorites_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_product_favorites_product
        FOREIGN KEY (product_id)
        REFERENCES products(id)
        ON DELETE CASCADE
) COMMENT = '영업사원이 자주 사용하는 제품을 즐겨찾기로 저장하는 테이블';


CREATE TABLE customers (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '고객 식별자',

    created_by BIGINT NOT NULL COMMENT '고객 정보를 등록한 영업사원 ID',
    company_name VARCHAR(200) NOT NULL COMMENT '고객사 또는 거래처명',

    contact_name VARCHAR(100) NULL COMMENT '고객사 담당자명',
    email VARCHAR(255) NULL COMMENT '고객 이메일. 견적서 발송에 사용',

    phone VARCHAR(20) NULL COMMENT '고객 연락처',
    business_number VARCHAR(30) NULL COMMENT '사업자등록번호',
    address TEXT NULL COMMENT '고객사 주소',
    memo TEXT NULL COMMENT '고객 관련 메모',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '고객 정보 생성 일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '고객 정보 수정 일시',

    CONSTRAINT fk_customers_created_by
        FOREIGN KEY (created_by)
        REFERENCES users(id)
        ON DELETE RESTRICT
) COMMENT = '견적서 작성 시 사용할 고객사, 담당자, 이메일, 연락처 정보를 저장하는 테이블';


CREATE TABLE discount_policies (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '할인 정책 식별자',

    name VARCHAR(100) NOT NULL COMMENT '할인 정책명',
    
	target_type ENUM('ALL', 'CATEGORY', 'PRODUCT') NOT NULL DEFAULT 'ALL' COMMENT '정책 적용 대상, all=전체, category=특정카테고리별, product=특정제품별',
	category_id BIGINT NULL COMMENT 'target_type이 category일 때 카테고리 id 적용',
    product_id BIGINT NULL COMMENT 'target_type이 product일 때 제품 id 적용',   

    max_discount_rate DECIMAL(5, 2) NOT NULL DEFAULT 0.00 COMMENT '승인 없이 적용 가능한 최대 할인율',
    min_profit_rate DECIMAL(5, 2) NOT NULL DEFAULT 0.00 COMMENT '승인 없이 허용되는 최소 이익률',
    high_amount_threshold DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '고액 견적으로 판단하는 기준 금액',

    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT '현재 적용 중인 정책 여부. 실제 서비스에서는 활성 정책이 1개만 유지되도록 처리한다',
    effective_from DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '정책 적용 시작 일시',
    effective_to DATETIME NULL COMMENT '정책 적용 종료 일시. NULL이면 종료일 없음',

    created_by BIGINT NULL COMMENT '정책을 생성한 관리자 ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '정책 생성 일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '정책 수정 일시',

    CONSTRAINT fk_discount_policies_created_by
        FOREIGN KEY (created_by)
        REFERENCES users(id)
        ON DELETE SET NULL,
	
    CONSTRAINT fk_discount_policies_category
		FOREIGN KEY (category_id)
        REFERENCES categories(id)
        ON DELETE SET NULL,
        
	CONSTRAINT fk_discount_policies_product
		FOREIGN KEY (product_id)
        REFERENCES products(id)
        ON DELETE SET NULL
        
) COMMENT = '승인 필요 여부 판단에 사용하는 최대 할인율, 최소 이익률, 고액 견적 기준을 관리하는 테이블';



CREATE TABLE quotes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '견적 식별자',

    discount_policy_id BIGINT NULL COMMENT '견적 작성 시 승인 필요 여부 판단에 사용된 할인 정책 ID',

    quote_number VARCHAR(50) NOT NULL COMMENT '견적 번호 (예: Q-2026-0001)',
    customer_id BIGINT NOT NULL COMMENT '견적 대상 고객 ID',
    created_by BIGINT NOT NULL COMMENT '견적을 작성한 영업사원 ID',

    original_quote_id BIGINT NULL COMMENT '수정본, 재작성 견적, 재사용 견적의 원본 견적 ID',
    version_no INT NOT NULL DEFAULT 1 COMMENT '견적 버전 번호 (최초 견적은 1, 수정본은 2 이상)',
    is_latest BOOLEAN NOT NULL DEFAULT TRUE COMMENT '동일 원본 견적 그룹 내 최신 견적 여부',

    status ENUM(
        'DRAFT',
        'SUBMITTED',
        'APPROVAL_NOT_REQUIRED',
        'APPROVAL_PENDING',
        'APPROVED',
        'REJECTED',
        'REVISING',
        'SENT',
        'EXPIRED',
        'CANCELLED'
    ) NOT NULL DEFAULT 'DRAFT' COMMENT '견적 상태: 임시저장, 작성완료, 승인불필요, 승인대기, 승인완료, 반려, 수정중, 발송완료, 만료, 취소',

    company_name VARCHAR(100) NULL COMMENT '발행 시점 자사명',
    company_business_number VARCHAR(20) NULL COMMENT '발행 시점 자사 사업자번호',
    company_email VARCHAR(100) NULL COMMENT '발행 시점 자사 이메일',
    company_phone VARCHAR(30) NULL COMMENT '발행 시점 자사 연락처',
    company_address TEXT NULL COMMENT '발행 시점 자사 주소지',
    customer_company_name VARCHAR(100) NULL COMMENT '발행 시점 거래처명',
    customer_contact_name VARCHAR(50) NULL COMMENT '발행 시점 고객 담당자명',
    customer_email VARCHAR(100) NULL COMMENT '발행 시점 고객 이메일',
    customer_phone VARCHAR(30) NULL COMMENT '발행 시점 고객 연락처',
    customer_address TEXT NULL COMMENT '발행 시점 고객 주소지',

    issued_date DATE NULL COMMENT '견적 발행일',
    delivery_term VARCHAR(100) NULL COMMENT '납기 및 인도 조건',

    subtotal DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '할인 및 VAT 적용 전 상품 금액 합계',
    discount_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '견적 전체 할인 금액 합계',
    supply_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '할인 적용 후 VAT 제외 공급가액',
    tax_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT 'VAT 금액',
    total_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT 'VAT 포함 최종 견적 금액',

    total_cost_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '견적 항목의 총 원가 합계',
    expected_profit_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '예상 이익금 (supply_amount - total_cost_amount 기준으로 계산)',
    profit_rate DECIMAL(7, 2) NOT NULL DEFAULT 0.00 COMMENT '이익률 (예상 이익금 / VAT 제외 공급가액 * 100. 역마진 등 예외 상황을 고려해 DECIMAL(7,2) 사용)',

    approval_required BOOLEAN NOT NULL DEFAULT FALSE COMMENT '할인율 초과, 최소 이익률 미달, 고액 견적 조건에 따른 승인 필요 여부',
    internal_memo TEXT NULL COMMENT '내부 검토용 메모 (고객에게 전달되지 않음)',
    valid_until DATE NULL COMMENT '견적서 유효 기간',

    submitted_at DATETIME NULL COMMENT '견적 작성 완료 일시',
    approved_at DATETIME NULL COMMENT '견적 승인 완료 일시',
    sent_at DATETIME NULL COMMENT '견적서 이메일 발송 완료 일시',
    expired_at DATETIME NULL COMMENT '견적 만료 일시',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '견적 생성 일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '견적 수정 일시',

    CONSTRAINT uk_quotes_quote_number UNIQUE (quote_number),

    CONSTRAINT fk_quotes_discount_policy
        FOREIGN KEY (discount_policy_id)
        REFERENCES discount_policies(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_quotes_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_quotes_created_by
        FOREIGN KEY (created_by)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_quotes_original
        FOREIGN KEY (original_quote_id)
        REFERENCES quotes(id)
        ON DELETE SET NULL
) COMMENT = '견적 기본 정보, 상태, 버전, 금액 계산 결과, 내부 분석 정보를 저장하는 테이블';



CREATE TABLE quote_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '견적 항목 식별자',

    quote_id BIGINT NOT NULL COMMENT '소속 견적 ID',

    product_id BIGINT NULL COMMENT '선택한 제품 ID. 수동 입력 항목이면 NULL 가능',
    product_name VARCHAR(255) NOT NULL COMMENT '견적 작성 당시 제품명 스냅샷. 제품명이 변경되어도 견적서는 유지됨',
    product_code VARCHAR(100) NULL COMMENT '견적 작성 당시 제품 코드 스냅샷',
    spec VARCHAR(200) NULL COMMENT '제품 규격/스펙',

    unit_price DECIMAL(15, 2) NOT NULL COMMENT '견적 작성 당시 판매 단가',
    cost_price DECIMAL(15, 2) NOT NULL DEFAULT 0.00 COMMENT '견적 작성 당시 제품 원가',

    quantity DECIMAL(12, 2) NOT NULL DEFAULT 1.00 COMMENT '견적 수량. EA뿐 아니라 KG, M 등 소수 수량을 고려해 DECIMAL 사용',

    discount_rate DECIMAL(5, 2) NOT NULL DEFAULT 0.00 COMMENT '항목 할인율',
    discount_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '항목 할인 금액',

    vat_applicable BOOLEAN NOT NULL DEFAULT TRUE COMMENT '견적 작성 당시 VAT 적용 여부 스냅샷',
    vat_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '항목 VAT 금액',

    line_supply_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '항목별 VAT 제외 공급가액',
    line_total DECIMAL(18, 2) NOT NULL COMMENT '항목 최종 금액. 수량, 단가, 할인, VAT를 반영한 금액',

    discount_reason VARCHAR(255) NULL COMMENT '할인 적용 사유 (선택 입력)',

    sort_order INT NOT NULL DEFAULT 0 COMMENT '견적서 내 항목 표시 순서',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '견적 항목 생성 일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '견적 항목 수정 일시',

    CONSTRAINT fk_quote_items_quote
        FOREIGN KEY (quote_id)
        REFERENCES quotes(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_quote_items_product
        FOREIGN KEY (product_id)
        REFERENCES products(id)
        ON DELETE SET NULL
) COMMENT = '견적서에 포함되는 제품 항목, 수량, 단가, 할인, VAT, 항목별 금액을 저장하는 테이블';


CREATE TABLE quote_approval_reasons (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '견적 승인 필요 사유 식별자',

    quote_id BIGINT NOT NULL COMMENT '승인 필요 사유가 발생한 견적 ID',

    reason_type ENUM(
        'DISCOUNT_EXCEEDED',
        'LOW_PROFIT',
        'HIGH_AMOUNT'
    ) NOT NULL COMMENT '승인 필요 사유 유형 (할인율 초과, 낮은 이익률, 고액 견적)',

    reason_message VARCHAR(500) NULL COMMENT '승인 필요 사유 상세 설명',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '승인 필요 사유 생성 일시',

    CONSTRAINT uk_quote_approval_reasons_quote_reason UNIQUE (quote_id, reason_type),

    CONSTRAINT fk_quote_approval_reasons_quote
        FOREIGN KEY (quote_id)
        REFERENCES quotes(id)
        ON DELETE CASCADE
) COMMENT = '견적 승인 필요 사유를 다중 저장하는 테이블';


CREATE TABLE approval_requests (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '견적 승인 요청 식별자',

    quote_id BIGINT NOT NULL COMMENT '승인 요청 대상 견적 ID',

    requester_id BIGINT NOT NULL COMMENT '승인을 요청한 영업사원 ID',
    approver_id BIGINT NULL COMMENT '승인 처리할 관리자 ID (NULL이면 역할 기반으로 처리 가능)',

    status ENUM('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED') NOT NULL DEFAULT 'PENDING' COMMENT '승인 요청 상태: 대기, 승인, 반려, 취소',

    request_memo TEXT NULL COMMENT '영업사원이 작성한 승인 요청 사유',
    reject_reason TEXT NULL COMMENT '관리자가 입력한 반려 사유',
    approve_memo TEXT NULL COMMENT '관리자가 승인 시 입력한 메모',

    ai_risk_summary TEXT NULL COMMENT '할인율, 총액, 이익률 등을 기반으로 생성된 AI 리스크 요약',
    request_count INT NOT NULL DEFAULT 1 COMMENT '동일 견적에 대한 승인 요청 또는 재요청 횟수',

    requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '승인 요청 일시',
    processed_at DATETIME NULL COMMENT '승인 또는 반려 처리 일시',

    CONSTRAINT fk_approval_requests_quote
        FOREIGN KEY (quote_id)
        REFERENCES quotes(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_approval_requests_requester
        FOREIGN KEY (requester_id)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_approval_requests_approver
        FOREIGN KEY (approver_id)
        REFERENCES users(id)
        ON DELETE SET NULL
) COMMENT = '할인율 초과, 이익률 미달, 고액 견적 등에 따른 견적 승인 요청을 저장하는 테이블';


CREATE TABLE quote_approval_histories (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '견적 승인 이력 식별자',

    approval_request_id BIGINT NOT NULL COMMENT '연결된 견적 승인 요청 ID',
    actor_id BIGINT NOT NULL COMMENT '이력을 발생시킨 사용자 ID (요청자는 영업사원, 승인/반려자는 관리자)',

    action ENUM('REQUESTED', 'APPROVED', 'REJECTED', 'RE_REQUESTED', 'CANCELLED') NOT NULL COMMENT '승인 요청 처리 이력 유형',
    before_status ENUM('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED') NULL COMMENT '처리 전 승인 요청 상태',
    after_status ENUM('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED') NULL COMMENT '처리 후 승인 요청 상태',

    memo TEXT NULL COMMENT '요청 메모, 승인 메모, 반려 사유, 재요청 사유',
    acted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '승인 이력이 발생한 일시',

    CONSTRAINT fk_quote_approval_histories_request
        FOREIGN KEY (approval_request_id)
        REFERENCES approval_requests(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_quote_approval_histories_actor
        FOREIGN KEY (actor_id)
        REFERENCES users(id)
        ON DELETE RESTRICT
) COMMENT = '견적 승인 요청, 승인, 반려, 재요청, 취소 이력을 저장하는 테이블';



CREATE TABLE email_sends (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '이메일 발송 이력 식별자',

    quote_id BIGINT NOT NULL COMMENT '발송한 견적 ID',
    sent_by BIGINT NOT NULL COMMENT '이메일을 발송한 사용자 ID',

    to_email VARCHAR(255) NOT NULL COMMENT '수신자 이메일 주소',
    subject VARCHAR(500) NOT NULL COMMENT '이메일 제목',
    body TEXT NULL COMMENT '이메일 본문',

    status ENUM('PENDING', 'SENT', 'FAILED') NOT NULL DEFAULT 'PENDING' COMMENT '이메일 발송 상태',
    failure_reason VARCHAR(500) NULL COMMENT '이메일 발송 실패 사유',

    sent_at DATETIME NULL COMMENT '이메일 발송 성공 일시',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '이메일 발송 요청 일시',

    CONSTRAINT fk_email_sends_quote
        FOREIGN KEY (quote_id)
        REFERENCES quotes(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_email_sends_sent_by
        FOREIGN KEY (sent_by)
        REFERENCES users(id)
        ON DELETE RESTRICT
) COMMENT = '승인된 견적서를 고객 이메일로 발송한 이력과 결과를 저장하는 테이블';



CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '알림 식별자',

    user_id BIGINT NOT NULL COMMENT '알림을 받을 사용자 ID',

    type ENUM(
		'USER_CREATED', 
		'USER_SUSPENDED', 
		'USER_REACTIVATED', 
		'PASSWORD_RESET', 
		'ROLE_CHANGED', 
		'APPROVAL_REQUESTED', 
		'QUOTE_APPROVED', 
		'QUOTE_REJECTED', 
		'QUOTE_EXPIRING', 
		'EMAIL_SENT', 
		'EMAIL_FAILED', 
		'SYSTEM'
	) NOT NULL COMMENT '알림 유형',
    title VARCHAR(255) NOT NULL COMMENT '알림 제목',
    message TEXT NULL COMMENT '알림 상세 내용',
    related_type ENUM('USER', 'QUOTE', 'APPROVAL', 'EMAIL', 'SYSTEM') NULL COMMENT '알림과 연결된 도메인 유형',
    related_id BIGINT NULL COMMENT '알림과 연결된 데이터 ID',

    is_read BOOLEAN NOT NULL DEFAULT FALSE COMMENT '알림 읽음 여부',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '알림 생성 일시',
    read_at DATETIME NULL COMMENT '알림 읽은 일시',

    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
) COMMENT = '계정 생성, 계정 상태 변경, 견적 승인 요청, 반려, 이메일 발송 결과 등 사용자 알림을 저장하는 테이블';


CREATE TABLE guide_confirmations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '가이드 확인 이력 식별자',
    user_id BIGINT NOT NULL COMMENT '가이드를 확인한 사용자 ID',
    guide_type ENUM('QUOTE_WRITE_GUIDE', 'MANAGER_OPERATIONS_GUIDE') NOT NULL COMMENT '가이드 유형',
    confirmed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '확인 완료 버튼을 누른 일시',

    CONSTRAINT uk_guide_confirmations_user_type UNIQUE (user_id, guide_type),

    CONSTRAINT fk_guide_confirmations_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
) COMMENT = '견적 작성 절차, 할인율 기준, 승인 요청 조건 등 가이드 확인 완료 여부를 저장하는 테이블';


-- ================================================================================================================================
-- 조회 성능 개선용 인덱스
-- ================================================================================================================================

-- users 테이블 인덱스
-- 회원번호 기반 로그인과 사용자 검색 시 사용한다.
CREATE INDEX idx_users_member_number ON users (member_number);
-- 권한별 사용자 목록을 조회할 때 role 조건으로 검색 범위를 줄여 사용자 관리 화면의 필터 조회 속도를 개선한다.
CREATE INDEX idx_users_role ON users (role);

-- 활성, 정지, 삭제 사용자처럼 status 조건으로 사용자를 조회할 때 전체 사용자 테이블 스캔을 줄인다.
CREATE INDEX idx_users_status ON users (status);


-- training_contents 테이블 인덱스
-- 견적 작성 화면 진입 시 활성화된 필수 교육 콘텐츠를 training_type과 active 조건으로 빠르게 찾기 위해 사용한다.
CREATE INDEX idx_training_contents_type_active ON training_contents (training_type, active);


CREATE INDEX idx_training_videos_content_active ON training_videos (training_content_id, active, sort_order);

CREATE INDEX idx_user_training_video_progress_user ON user_training_video_progress (user_id);

CREATE INDEX idx_user_training_video_progress_video ON user_training_video_progress (training_video_id);


-- user_training_progress 테이블 인덱스
-- 견적 작성 가능 여부 확인 시 user_id와 status로 사용자의 교육 이수 상태를 빠르게 조회하기 위해 사용한다.
CREATE INDEX idx_user_training_progress_user_status ON user_training_progress (user_id, status);

-- 관리자 교육 이수 현황 화면에서 특정 교육 콘텐츠를 수강한 사용자 목록을 조회할 때 사용한다.
CREATE INDEX idx_user_training_progress_content ON user_training_progress (training_content_id);


-- password_reset_tokens 테이블 인덱스
-- 비밀번호 재설정 확인 시 token_hash 조건으로 재설정 토큰을 빠르게 조회한다.
CREATE INDEX idx_password_reset_tokens_token_hash ON password_reset_tokens (token_hash);

-- 특정 사용자의 토큰을 목적별로 조회하거나 기존 미사용 토큰을 만료 처리할 때 사용한다.
CREATE INDEX idx_password_reset_tokens_user_purpose ON password_reset_tokens (user_id, purpose);

-- 만료된 재설정 토큰 정리 작업 시 expires_at 기준 조회 성능을 개선한다.
CREATE INDEX idx_password_reset_tokens_expires_at ON password_reset_tokens (expires_at);


-- categories 테이블 인덱스
-- categories 테이블의 parent_id 컬럼 조회 성능 개선용 인덱스
CREATE INDEX idx_categories_parent ON categories (parent_id);


-- products 테이블 인덱스
-- products 테이블의 category_id 컬럼 조회 성능 개선용 인덱스
CREATE INDEX idx_products_category ON products (category_id);

-- products 테이블의 name 컬럼 조회 성능 개선용 인덱스
CREATE INDEX idx_products_name ON products (name);


-- product_favorites 테이블 인덱스
-- product_favorites 테이블의 product_id 컬럼 조회 성능 개선용 인덱스
CREATE INDEX idx_product_favorites_product ON product_favorites (product_id);


-- customers 테이블 인덱스
-- customers 테이블의 created_by 컬럼 조회 성능 개선용 인덱스
CREATE INDEX idx_customers_created_by ON customers (created_by);

-- customers 테이블의 company_name 컬럼 조회 성능 개선용 인덱스
CREATE INDEX idx_customers_company_name ON customers (company_name);


-- discount_policies 테이블 인덱스
-- 견적 작성 시 현재 활성 상태인 할인 정책을 빠르게 조회한다.
CREATE INDEX idx_discount_policies_active ON discount_policies (is_active);

-- 정책 적용 시작일과 종료일 기준으로 특정 시점에 적용 가능한 할인 정책을 조회할 때 사용한다.
CREATE INDEX idx_discount_policies_effective_period ON discount_policies (effective_from, effective_to);


-- quotes 테이블 인덱스
-- quotes 테이블의 customer_id 컬럼 조회 성능 개선용 인덱스
CREATE INDEX idx_quotes_customer ON quotes (customer_id);

-- 영업사원 메인 화면과 내 견적 목록에서 작성자별 견적 목록을 created_by 기준으로 빠르게 조회한다.
CREATE INDEX idx_quotes_created_by ON quotes (created_by);

-- 승인대기, 반려, 발송완료, 만료 등 견적 상태별 목록 조회 시 status 조건 검색 속도를 개선한다.
CREATE INDEX idx_quotes_status ON quotes (status);

-- 내 견적 목록에서 작성자와 상태를 함께 필터링하고 최신순으로 정렬할 때 복합 조건 조회 성능을 높인다.
CREATE INDEX idx_quotes_created_by_status_created_at ON quotes (created_by, status, created_at);

-- 원본 견적 기준으로 수정본, 재작성 견적, 재사용 견적 목록을 조회할 때 사용한다.
CREATE INDEX idx_quotes_original ON quotes (original_quote_id);

-- 동일 원본 견적 그룹에서 최신 견적만 빠르게 조회할 때 사용한다.
CREATE INDEX idx_quotes_original_latest ON quotes (original_quote_id, is_latest);

-- 동일 원본 견적 그룹에서 버전 순서대로 견적 이력을 조회할 때 사용한다.
CREATE INDEX idx_quotes_original_version ON quotes (original_quote_id, version_no);

-- 관리자 대시보드에서 기간별 작성 완료 견적 수, 승인율, 반려율, 평균 할인율, 평균 이익률을 조회할 때 사용한다.
CREATE INDEX idx_quotes_latest_submitted_status ON quotes (is_latest, submitted_at, status);


-- quote_items 테이블 인덱스
-- quote_items 테이블의 quote_id 컬럼 조회 성능 개선용 인덱스
CREATE INDEX idx_quote_items_quote ON quote_items (quote_id);

-- quote_items 테이블의 product_id 컬럼 조회 성능 개선용 인덱스
CREATE INDEX idx_quote_items_product ON quote_items (product_id);

-- 인기 제품 TOP N 조회 시 제품 기준으로 견적 항목을 찾고 quotes와 조인하기 위해 사용한다.
CREATE INDEX idx_quote_items_product_quote ON quote_items (product_id, quote_id);


-- quote_approval_reasons 테이블 인덱스
-- 견적 상세 화면에서 quote_id 기준으로 승인 필요 사유 목록을 빠르게 조회한다.
CREATE INDEX idx_quote_approval_reasons_quote ON quote_approval_reasons (quote_id);

-- 관리자 승인 검토 화면에서 승인 필요 사유 유형별 견적을 조회할 때 사용한다.
CREATE INDEX idx_quote_approval_reasons_type ON quote_approval_reasons (reason_type);


-- approval_requests 테이블 인덱스
-- approval_requests 테이블의 quote_id 컬럼 조회 성능 개선용 인덱스
CREATE INDEX idx_approval_requests_quote ON approval_requests (quote_id);

-- approval_requests 테이블의 requester_id 컬럼 조회 성능 개선용 인덱스
CREATE INDEX idx_approval_requests_requester ON approval_requests (requester_id);

-- approval_requests 테이블의 approver_id 컬럼 조회 성능 개선용 인덱스
CREATE INDEX idx_approval_requests_approver ON approval_requests (approver_id);

-- 관리자 승인 대기 견적 목록에서 PENDING 상태 승인 요청만 빠르게 조회한다.
CREATE INDEX idx_approval_requests_status ON approval_requests (status);


-- quote_approval_histories 테이블 인덱스
-- quote_approval_histories 테이블의 approval_request_id 컬럼 조회 성능 개선용 인덱스
CREATE INDEX idx_quote_approval_histories_request ON quote_approval_histories (approval_request_id);

-- quote_approval_histories 테이블의 actor_id 컬럼 조회 성능 개선용 인덱스
CREATE INDEX idx_quote_approval_histories_actor ON quote_approval_histories (actor_id);



-- email_sends 테이블 인덱스
-- email_sends 테이블의 quote_id 컬럼 조회 성능 개선용 인덱스
CREATE INDEX idx_email_sends_quote ON email_sends (quote_id);

-- email_sends 테이블의 sent_by 컬럼 조회 성능 개선용 인덱스
CREATE INDEX idx_email_sends_sent_by ON email_sends (sent_by);

-- email_sends 테이블의 status 컬럼 조회 성능 개선용 인덱스
CREATE INDEX idx_email_sends_status ON email_sends (status);


-- notifications 테이블 인덱스
-- notifications 테이블의 user_id 컬럼 조회 성능 개선용 인덱스
CREATE INDEX idx_notifications_user_read_created ON notifications (user_id, is_read, created_at DESC);
