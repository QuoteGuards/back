package com.project.back.global.storage;

import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

// AWS S3 영상 저장 (app.storage.type=s3 일 때 활성화)
@Slf4j
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3VideoFileStorage implements VideoFileStorage {

    private final S3Client s3Client;
    private final String bucket;
    private final String publicBaseUrl;

    public S3VideoFileStorage(
            S3Client s3Client,
            @Value("${app.storage.s3.bucket}") String bucket,
            @Value("${app.storage.s3.region}") String region,
            @Value("${app.storage.s3.public-url:}") String publicUrlOverride) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.publicBaseUrl = (publicUrlOverride != null && !publicUrlOverride.isBlank())
                ? trimSlash(publicUrlOverride)
                : "https://" + bucket + ".s3." + region + ".amazonaws.com";
    }

    @Override
    public String store(MultipartFile file, String dir) {
        validateVideo(file);

        String key = dir + "/" + UUID.randomUUID().toString().replace("-", "") + videoExtensionOf(file.getOriginalFilename());
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(file.getContentType() != null ? file.getContentType() : "video/mp4")
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );
        } catch (IOException | SdkException e) {
            log.error("S3 영상 업로드 실패 (key={})", key, e);
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        return publicBaseUrl + "/" + key;
    }

    private static String trimSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
