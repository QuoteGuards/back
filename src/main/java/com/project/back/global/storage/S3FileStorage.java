package com.project.back.global.storage;

import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

// AWS S3 저장 (app.storage.type=s3 일 때 활성화)
@Slf4j
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3FileStorage implements FileStorage {

    private final S3Client s3Client;
    private final String bucket;
    private final String publicBaseUrl;

    public S3FileStorage(
            S3Client s3Client,
            @Value("${app.storage.s3.bucket}") String bucket,
            @Value("${app.storage.s3.region}") String region,
            @Value("${app.storage.s3.public-url:}") String publicUrlOverride) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        // CloudFront 등 커스텀 도메인이 있으면 override, 없으면 표준 S3 URL
        this.publicBaseUrl = (publicUrlOverride != null && !publicUrlOverride.isBlank())
                ? trimSlash(publicUrlOverride)
                : "https://" + bucket + ".s3." + region + ".amazonaws.com";
    }

    @Override
    public String store(MultipartFile file, String dir) {
        validateImage(file);

        String key = dir + "/" + UUID.randomUUID().toString().replace("-", "") + extensionOf(file.getOriginalFilename());
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(file.getContentType())
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );
        } catch (IOException e) {
            log.error("S3 업로드 실패 (key={})", key, e);
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        return publicBaseUrl + "/" + key;
    }

    private static String trimSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
