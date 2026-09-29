package com.manacommunity.media.controller;

import com.manacommunity.media.domain.entity.MediaFile;
import com.manacommunity.media.domain.entity.MediaFile.*;
import com.manacommunity.media.dto.*;
import com.manacommunity.media.security.MediaPrincipal;
import com.manacommunity.media.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Media REST controller.
 *
 * <h3>Upload paths</h3>
 * <ol>
 *   <li>{@code POST /api/media/files/upload} — server-mediated multipart (small files)</li>
 *   <li>{@code POST /api/media/files/presigned-url} → client PUTs binary to S3 → {@code POST /api/media/files/confirm}</li>
 * </ol>
 */
@Tag(name = "Media", description = "File upload, access, and management")
@RestController
@RequestMapping("/api/media/files")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    // ─── Upload: Server-Mediated Multipart ────────────────────────────────────

    @Operation(summary = "Upload file via server (multipart) — recommended for files < 10 MB")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaFileResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam OwnerType ownerType,
            @RequestParam(required = false) Long ownerId,
            @RequestParam MediaFile.MediaType mediaType,
            @RequestParam(defaultValue = "PUBLIC") AccessLevel accessLevel,
            @AuthenticationPrincipal MediaPrincipal principal) {

        UploadRequest req = new UploadRequest(
                principal.getCommunityId(), ownerType, ownerId, mediaType, accessLevel);
        MediaFileResponse response = mediaService.uploadFile(
                file, req, principal.getCommunityId(), principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ─── Upload: Pre-signed URL Generation ───────────────────────────────────

    @Operation(summary = "Request a pre-signed S3 PUT URL for direct client upload (large files)")
    @PostMapping("/presigned-url")
    public ResponseEntity<PresignedUploadResponse> requestPresignedUrl(
            @Valid @RequestBody PresignedUploadRequest req,
            @AuthenticationPrincipal MediaPrincipal principal) {

        return ResponseEntity.ok(
                mediaService.requestPresignedUploadUrl(req, principal.getCommunityId()));
    }

    @Operation(summary = "Confirm a completed S3 direct upload and register metadata in DB")
    @PostMapping("/confirm")
    public ResponseEntity<MediaFileResponse> confirmUpload(
            @Valid @RequestBody ConfirmUploadRequest req,
            @AuthenticationPrincipal MediaPrincipal principal) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                mediaService.confirmUpload(req, principal.getCommunityId(), principal.getUserId()));
    }

    // ─── Read ─────────────────────────────────────────────────────────────────

    @Operation(summary = "Get media file metadata by ID")
    @GetMapping("/{id}")
    public ResponseEntity<MediaFileResponse> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal MediaPrincipal principal) {

        return ResponseEntity.ok(mediaService.getFileById(id, principal.getCommunityId()));
    }

    @Operation(summary = "Get a time-limited access URL for the file (CDN or pre-signed GET)")
    @GetMapping("/{id}/access")
    public ResponseEntity<Map<String, String>> getAccessUrl(
            @PathVariable Long id,
            @AuthenticationPrincipal MediaPrincipal principal) {

        String url = mediaService.getAccessUrl(id, principal.getCommunityId());
        return ResponseEntity.ok(Map.of("url", url));
    }

    @Operation(summary = "List all media files for the community (paginated)")
    @GetMapping
    public ResponseEntity<Page<MediaFileResponse>> list(
            @AuthenticationPrincipal MediaPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(
                mediaService.listByCommunity(principal.getCommunityId(), pageable));
    }

    @Operation(summary = "List media files by owner (e.g., userId, tournamentId)")
    @GetMapping("/by-owner")
    public ResponseEntity<Page<MediaFileResponse>> listByOwner(
            @RequestParam OwnerType ownerType,
            @RequestParam Long ownerId,
            @AuthenticationPrincipal MediaPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(
                mediaService.listByOwner(principal.getCommunityId(), ownerType, ownerId, pageable));
    }

    @Operation(summary = "List media files by type (AVATAR, BANNER, etc.)")
    @GetMapping("/by-type")
    public ResponseEntity<Page<MediaFileResponse>> listByType(
            @RequestParam MediaFile.MediaType mediaType,
            @AuthenticationPrincipal MediaPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(
                mediaService.listByMediaType(principal.getCommunityId(), mediaType, pageable));
    }

    // ─── Storage Stats ────────────────────────────────────────────────────────

    @Operation(summary = "Get total storage usage in bytes for the community")
    @GetMapping("/storage-usage")
    public ResponseEntity<Map<String, Long>> storageUsage(
            @AuthenticationPrincipal MediaPrincipal principal) {

        long bytes = mediaService.getStorageUsageBytes(principal.getCommunityId());
        return ResponseEntity.ok(Map.of(
                "bytes", bytes,
                "kilobytes", bytes / 1024,
                "megabytes", bytes / (1024 * 1024)
        ));
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    @Operation(summary = "Soft-delete a media file (recoverable)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDelete(
            @PathVariable Long id,
            @AuthenticationPrincipal MediaPrincipal principal) {

        mediaService.softDelete(id, principal.getCommunityId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Permanently delete a media file from S3 and DB")
    @DeleteMapping("/{id}/hard")
    public ResponseEntity<Void> hardDelete(
            @PathVariable Long id,
            @AuthenticationPrincipal MediaPrincipal principal) {

        mediaService.hardDelete(id, principal.getCommunityId());
        return ResponseEntity.noContent().build();
    }
}
