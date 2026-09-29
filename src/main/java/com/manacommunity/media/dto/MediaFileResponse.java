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
        Instant createdAt,
        Instant updatedAt
) {}
