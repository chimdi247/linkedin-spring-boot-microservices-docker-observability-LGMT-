package com.linkedin.postservice.service;

import com.linkedin.postservice.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.region}")
    private String region;

    /**
     * Base URL browsers use to download objects, e.g. {@code http://localhost:8088/media} (nginx -> MinIO).
     * Empty = virtual-hosted AWS S3 URL.
     */
    @Value("${aws.s3.public-url:}")
    private String publicUrl;

    /**
     * Upload an image to S3 and return its public URL.
     */
    public String uploadImage(MultipartFile file, String keyPrefix) {
        String contentType = file.getContentType();
        if (file.isEmpty() || contentType == null || !contentType.startsWith("image/")) {
            throw ApiException.badRequest("Only image files are allowed");
        }
        try {
            String key = keyPrefix + "/" + UUID.randomUUID() + "_" + sanitize(file.getOriginalFilename());

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));

            String url = (publicUrl != null && !publicUrl.isBlank())
                    ? trimTrailingSlash(publicUrl) + "/" + key
                    : "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + key;

            log.info("File uploaded to S3: {}", url);
            return url;

        } catch (IOException e) {
            throw new IllegalStateException("Failed to read uploaded file", e);
        }
    }

    private static String sanitize(String name) {
        String n = (name == null || name.isBlank()) ? "file" : name;
        return n.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String trimTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
