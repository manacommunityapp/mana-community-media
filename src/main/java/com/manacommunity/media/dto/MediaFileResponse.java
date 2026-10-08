package com.manacommunity.media.dto;

import com.manacommunity.media.domain.entity.MediaFile.*;
import java.time.Instant;

/**
 * Response DTO representing a media file resource returned to clients.
 * Does NOT include raw binary data — clients access files via cdnUrl or /access/{id}.
 */
public record MediaFileResponse(
        Long id,
        Long communityId,
        OwnerType ownerType,
        Long ownerId,
        String originalName,
        String s3Key,
        String mimeType,
        Long sizeBytes,
        MediaType mediaType,
        AccessLevel accessLevel,
        String cdnUrl,           // null for PRIVATE files; use /media/files/{id}/access
        Integer widthPx,
        Integer heightPx,
        String thumbnailCdnUrl,  // null if not an image or not yet processed
        FileStatus status,
        ModerationStatus moderationStatus,
        RetentionTier retentionTier,
        Instant createdAt,
        Instant updatedAt
) {
    public MediaFileResponse(
            Long id, Long communityId, OwnerType ownerType, Long ownerId,
            String originalName, String s3Key, String mimeType, Long sizeBytes,
            MediaType mediaType, AccessLevel accessLevel, String cdnUrl,
            Integer widthPx, Integer heightPx, String thumbnailCdnUrl,
            FileStatus status, Instant createdAt, Instant updatedAt) {
        this(id, communityId, ownerType, ownerId, originalName, s3Key, mimeType,
             sizeBytes, mediaType, accessLevel, cdnUrl, widthPx, heightPx,
             thumbnailCdnUrl, status, ModerationStatus.APPROVED, RetentionTier.STANDARD,
             createdAt, updatedAt);
    }
}
