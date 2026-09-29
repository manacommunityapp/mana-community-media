package com.manacommunity.media.dto;

import com.manacommunity.media.domain.entity.MediaFile.*;
import jakarta.validation.constraints.*;

/**
 * Client requests a pre-signed S3 PUT URL.
 * No binary is transferred to the server — only metadata.
 */
public record PresignedUploadRequest(
        @NotNull OwnerType ownerType,
        Long ownerId,
        @NotNull MediaType mediaType,
        @NotNull AccessLevel accessLevel,
        @NotBlank String fileName,
        @NotBlank String mimeType,
        @Positive long fileSizeBytes
) {}
