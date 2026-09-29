package com.manacommunity.media.dto;

import com.manacommunity.media.domain.entity.MediaFile.*;
import jakarta.validation.constraints.*;

/**
 * Client confirms the S3 direct upload has completed.
 * Server verifies object exists and persists the metadata record.
 */
public record ConfirmUploadRequest(
        @NotBlank String s3Key,
        @NotNull OwnerType ownerType,
        Long ownerId,
        @NotNull MediaType mediaType,
        @NotNull AccessLevel accessLevel,
        @NotBlank String fileName,
        @NotBlank String mimeType,
        @Positive long fileSizeBytes
) {}
