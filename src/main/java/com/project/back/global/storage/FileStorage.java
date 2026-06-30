package com.project.back.global.storage;

import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import org.springframework.web.multipart.MultipartFile;

// 파일 저장 추상화 — 구현체(로컬/S3)를 app.storage.type 으로 전환
public interface FileStorage {

    long MAX_IMAGE_BYTES = 5 * 1024 * 1024; // 5MB

    // 파일을 dir 하위에 저장하고 접근 가능한 공개 URL 반환
    String store(MultipartFile file, String dir);

    // 이미지 업로드 공통 검증 (빈 파일/용량/타입)
    default void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.FILE_EMPTY);
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new CustomException(ErrorCode.FILE_TOO_LARGE);
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new CustomException(ErrorCode.FILE_INVALID_TYPE);
        }
        // 허용 확장자(jpg/jpeg/png/gif/webp/bmp)가 아니면 거부 — 저장 시 확장자 누락/우회 방지
        if (extensionOf(file.getOriginalFilename()).isEmpty()) {
            throw new CustomException(ErrorCode.FILE_INVALID_TYPE);
        }
    }

    // 원본 파일명에서 확장자 추출 (없으면 빈 문자열)
    default String extensionOf(String originalFilename) {
        if (originalFilename == null) return "";
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) return "";
        String ext = originalFilename.substring(dot).toLowerCase();
        // 안전한 확장자만 허용
        return ext.matches("\\.(jpg|jpeg|png|gif|webp|bmp)") ? ext : "";
    }
}
