package com.project.back.global.storage;

import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

// 로컬 디스크 저장 (기본). app.storage.type 미설정/local 일 때 활성화
// 저장된 파일은 WebConfig 의 /uploads/** 정적 매핑으로 서빙됨
@Slf4j
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

    private final Path root;
    private final String publicBaseUrl;

    public LocalFileStorage(
            @Value("${app.storage.local.dir:./uploads}") String dir,
            @Value("${app.storage.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("업로드 디렉토리 생성 실패: " + this.root, e);
        }
    }

    @Override
    public String store(MultipartFile file, String dir) {
        validateImage(file);

        String filename = UUID.randomUUID().toString().replace("-", "") + extensionOf(file.getOriginalFilename());
        try {
            Path targetDir = root.resolve(dir).normalize();
            Files.createDirectories(targetDir);
            file.transferTo(targetDir.resolve(filename));
        } catch (IOException e) {
            log.error("로컬 파일 저장 실패 (dir={})", dir, e);
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        return publicBaseUrl + "/uploads/" + dir + "/" + filename;
    }
}
