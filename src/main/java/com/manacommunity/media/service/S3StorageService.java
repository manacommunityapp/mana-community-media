package com.manacommunity.media.service;

import com.manacommunity.media.config.S3Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;

import java.io.InputStream;
import java.net.URL;
import java.time.Instant;
import java.util.Map;

/**
 * Low-level wrapper around the AWS S3 SDK.
 * <p>
 * All higher-level media logic lives in {@link MediaService}; this class only speaks to S3.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageService {

    private final S3Client       s3Client;
    private final S3Presigner    s3Presigner;
    private final S3Properties   props;

    // ─── Upload ──────────────────────────────────────────────────────────────

    /**
     * Directly uploads a byte array to S3 and returns the ETag.
     */
    public String uploadBytes(String bucket, String key, byte[] data,
                               String mimeType, Map<String, String> metadata,
                               String cannedAcl) {
        PutObjectRequest.Builder rb = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(mimeType)
                .contentLength((long) data.length);

        if (metadata != null && !metadata.isEmpty()) rb.metadata(metadata);

        PutObjectResponse response = s3Client.putObject(rb.build(), RequestBody.fromBytes(data));
        log.debug("Uploaded s3://{}/{} ({} bytes), ETag={}", bucket, key, data.length, response.eTag());
        return response.eTag();
    }

    /**
     * Directly uploads a stream to S3 when size is known.
     */
    public String uploadStream(String bucket, String key, InputStream stream,
                                long contentLength, String mimeType) {
        PutObjectResponse response = s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(mimeType)
                        .contentLength(contentLength)
                        .build(),
                RequestBody.fromInputStream(stream, contentLength)
        );
        return response.eTag();
    }

    // ─── Pre-signed URLs ─────────────────────────────────────────────────────

    /**
     * Generates a pre-signed GET URL for private assets with configurable TTL.
     */
    public URL generatePresignedGetUrl(String bucket, String key) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(props.presignedUrlTtl())
                .getObjectRequest(b -> b.bucket(bucket).key(key))
                .build();
        URL url = s3Presigner.presignGetObject(presignRequest).url();
        log.debug("Generated pre-signed GET URL for s3://{}/{}, TTL={}", bucket, key, props.presignedUrlTtl());
        return url;
    }

    /**
     * Generates a pre-signed PUT URL for direct-to-S3 browser uploads.
     */
    public PresignedPutUrl generatePresignedPutUrl(String bucket, String key,
                                                    String mimeType, long maxSizeBytes) {
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(props.uploadPresignedTtl())
                .putObjectRequest(b -> b
                        .bucket(bucket)
                        .key(key)
                        .contentType(mimeType))
                .build();

        software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest presigned =
                s3Presigner.presignPutObject(presignRequest);

        Instant expiresAt = Instant.now().plus(props.uploadPresignedTtl());
        return new PresignedPutUrl(presigned.url().toString(), key, expiresAt);
    }

    // ─── Delete ──────────────────────────────────────────────────────────────

    public void deleteObject(String bucket, String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
        log.info("Deleted s3://{}/{}", bucket, key);
    }

    public void deleteObject(String key) {
        if (key == null) return;
        String bucket = key.startsWith("public") ? props.bucketPublic() : props.bucketPrivate();
        deleteObject(bucket, key);
    }

    // ─── Existence Check ─────────────────────────────────────────────────────

    public boolean objectExists(String bucket, String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    // ─── CDN URL ─────────────────────────────────────────────────────────────

    /**
     * Builds the permanent CDN URL for a public object key.
     */
    public String buildCdnUrl(String key) {
        return props.cdnBaseUrl().stripTrailing() + "/" + key.stripLeading();
    }

    // ─── DTO ─────────────────────────────────────────────────────────────────

    public record PresignedPutUrl(String uploadUrl, String s3Key, Instant expiresAt) {}
}
