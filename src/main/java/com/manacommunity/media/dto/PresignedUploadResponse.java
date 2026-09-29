package com.manacommunity.media.dto;

import java.time.Instant;

/**
 * Response DTO returned to the client after generating a pre-signed S3 upload URL.
 */
public record PresignedUploadResponse(
        String uploadUrl,    // PUT this URL directly with the file binary
        String s3Key,        // used in ConfirmUploadRequest after upload completes
        Instant expiresAt,   // URL validity window
        String bucket        // informational: which S3 bucket
) {}
