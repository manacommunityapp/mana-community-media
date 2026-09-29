package com.manacommunity.media.dto;

import com.manacommunity.media.domain.entity.MediaFile.*;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for server-mediated (multipart) file upload.
 * The actual binary is in the {@code MultipartFile} parameter of the controller.
 */
public record UploadRequest(
        @NotNull Long communityId,
        @NotNull OwnerType ownerType,
        Long ownerId,
        @NotNull MediaType mediaType,
        @NotNull AccessLevel accessLevel
) {}
