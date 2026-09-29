package com.manacommunity.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * AWS S3 + CDN configuration properties.
 * All properties are prefixed with {@code media.s3} in application.yml.
 */
@ConfigurationProperties(prefix = "media.s3")
public record S3Properties(
        String region,
        String bucketPublic,
        String bucketPrivate,
        String accessKey,
        String secretKey,
        String cdnBaseUrl,             // e.g. https://cdn.mana.community
        Duration presignedUrlTtl,      // default 15 minutes for private files
        Duration uploadPresignedTtl,   // pre-signed PUT URL TTL for direct upload
        long maxFileSizeBytes,          // max allowed upload size
        List<String> allowedMimeTypes, // allowed MIME types whitelist
        Map<String, ImageVariant> imageVariants // named resize variants
) {
    public record ImageVariant(int width, int height, String suffix) {}

    /** Convenience: default allowed types when none configured. */
    public List<String> effectiveMimeTypes() {
        if (allowedMimeTypes != null && !allowedMimeTypes.isEmpty()) return allowedMimeTypes;
        return List.of(
                "image/jpeg", "image/png", "image/webp", "image/gif", "image/avif",
                "video/mp4", "video/webm", "video/quicktime",
                "application/pdf", "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        );
    }
}
