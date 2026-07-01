package com.project.back.global.storage;

import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import org.springframework.web.multipart.MultipartFile;

// 교육 영상 저장 추상화 — 구현체(로컬/S3)를 app.storage.type 으로 전환
public interface VideoFileStorage {

    long MAX_VIDEO_BYTES = 300L * 1024 * 1024; // 300MB

    // 교육 영상(MP4)을 dir 하위에 저장하고 접근 가능한 공개 URL 반환
    String store(MultipartFile file, String dir);

    default void validateVideo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.FILE_EMPTY);
        }
        if (file.getSize() > MAX_VIDEO_BYTES) {
            throw new CustomException(ErrorCode.FILE_VIDEO_TOO_LARGE);
        }
        if (videoExtensionOf(file.getOriginalFilename()).isEmpty()) {
            throw new CustomException(ErrorCode.FILE_VIDEO_INVALID_TYPE);
        }
        String contentType = file.getContentType();
        if (contentType != null
                && !contentType.startsWith("video/")
                && !"application/octet-stream".equals(contentType)) {
            throw new CustomException(ErrorCode.FILE_VIDEO_INVALID_TYPE);
        }
    }

    default String videoExtensionOf(String originalFilename) {
        if (originalFilename == null) return "";
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) return "";
        String ext = originalFilename.substring(dot).toLowerCase();
        return ".mp4".equals(ext) ? ext : "";
    }
}
