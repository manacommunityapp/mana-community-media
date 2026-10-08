package com.manacommunity.media.service;

import com.manacommunity.media.config.S3Properties;
import com.manacommunity.media.domain.entity.MediaFile;
import com.manacommunity.media.domain.entity.MediaFile.*;
import com.manacommunity.media.domain.repository.MediaFileRepository;
import com.manacommunity.media.dto.*;
import com.manacommunity.media.exception.MediaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Core media business logic.
 *
 * <p>Handles three upload paths:
 * <ol>
 *   <li>Direct server-side multipart upload (small files, avatars)</li>
 *   <li>Pre-signed URL generation for direct client → S3 upload (large files)</li>
 *   <li>Async post-upload registration (client confirms after S3 upload)</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaService {

    private final S3StorageService         s3Service;
    private final ImageProcessingService   imageService;
    private final MediaFileRepository      repository;
    private final S3Properties             s3Props;
    private final Tika                     tika;

    // ─── Multipart Upload (server-mediated) ───────────────────────────────────

    @Transactional
    public MediaFileResponse uploadFile(MultipartFile file, UploadRequest req,
                                        Long communityId, Long userId) {
        byte[] bytes = readBytes(file);
        String mimeType = detectMimeType(bytes, file.getOriginalFilename());
        validateMimeType(mimeType);
        validateSize(bytes.length);

        String bucket = bucketFor(req.accessLevel());
        String s3Key  = buildS3Key(req.ownerType(), req.ownerId(), req.mediaType(),
                                    file.getOriginalFilename(), req.accessLevel());

        String etag = s3Service.uploadBytes(bucket, s3Key, bytes, mimeType, null, null);
        String cdnUrl = req.accessLevel() == AccessLevel.PUBLIC
                ? s3Service.buildCdnUrl(s3Key) : null;

        MediaFile.MediaFileBuilder builder = MediaFile.builder()
                .communityId(communityId)
                .ownerType(req.ownerType())
                .ownerId(req.ownerId())
                .originalName(file.getOriginalFilename())
                .s3Key(s3Key)
                .mimeType(mimeType)
                .sizeBytes((long) bytes.length)
                .mediaType(req.mediaType())
                .accessLevel(req.accessLevel())
                .cdnUrl(cdnUrl)
                .etag(etag)
                .status(FileStatus.ACTIVE);

        if (imageService.isImage(mimeType)) {
            int[] dims = imageService.getDimensions(bytes);
            builder.widthPx(dims[0]).heightPx(dims[1]);
            processThumbnailAsync(bytes, s3Key, bucket, communityId, builder);
        }

        MediaFile saved = repository.save(builder.build());
        log.info("Uploaded media file id={} s3Key={} communityId={}", saved.getId(), s3Key, communityId);
        return toResponse(saved);
    }

    // ─── Pre-signed URL Generation (client-side upload) ───────────────────────

    public PresignedUploadResponse requestPresignedUploadUrl(PresignedUploadRequest req,
                                                              Long communityId) {
        validateMimeType(req.mimeType());
        validateSize(req.fileSizeBytes());

        String bucket = bucketFor(req.accessLevel());
        String s3Key  = buildS3Key(req.ownerType(), req.ownerId(), req.mediaType(),
                                    req.fileName(), req.accessLevel());

        S3StorageService.PresignedPutUrl presigned = s3Service.generatePresignedPutUrl(
                bucket, s3Key, req.mimeType(), req.fileSizeBytes());

        return new PresignedUploadResponse(
                presigned.uploadUrl(),
                presigned.s3Key(),
                presigned.expiresAt(),
                bucket
        );
    }

    /**
     * Called after client completes S3 direct upload to register the file in DB.
     */
    @Transactional
    public MediaFileResponse confirmUpload(ConfirmUploadRequest req,
                                           Long communityId, Long userId) {
        // Verify the object exists in S3
        String bucket = bucketFor(req.accessLevel());
        if (!s3Service.objectExists(bucket, req.s3Key())) {
            throw new MediaException("Object not found in S3: " + req.s3Key());
        }

        String cdnUrl = req.accessLevel() == AccessLevel.PUBLIC
                ? s3Service.buildCdnUrl(req.s3Key()) : null;

        MediaFile saved = repository.save(MediaFile.builder()
                .communityId(communityId)
                .ownerType(req.ownerType())
                .ownerId(req.ownerId())
                .originalName(req.fileName())
                .s3Key(req.s3Key())
                .mimeType(req.mimeType())
                .sizeBytes(req.fileSizeBytes())
                .mediaType(req.mediaType())
                .accessLevel(req.accessLevel())
                .cdnUrl(cdnUrl)
                .status(FileStatus.ACTIVE)
                .build());

        log.info("Confirmed direct S3 upload, media id={}", saved.getId());
        return toResponse(saved);
    }

    // ─── Read / Access ────────────────────────────────────────────────────────

    public MediaFileResponse getFileById(Long id, Long communityId) {
        MediaFile file = repository
                .findByIdAndCommunityIdAndStatusNot(id, communityId, FileStatus.HARD_DELETED)
                .orElseThrow(() -> new MediaException("Media file not found: " + id));
        return toResponse(file);
    }

    /**
     * Returns a time-limited pre-signed GET URL for private files.
     * For public files, returns the permanent CDN URL.
     */
    public String getAccessUrl(Long id, Long communityId) {
        MediaFile file = repository
                .findByIdAndCommunityIdAndStatusNot(id, communityId, FileStatus.HARD_DELETED)
                .orElseThrow(() -> new MediaException("Media file not found: " + id));

        if (file.getAccessLevel() == AccessLevel.PUBLIC) {
            return file.getCdnUrl();
        }
        return s3Service.generatePresignedGetUrl(s3Props.bucketPrivate(), file.getS3Key()).toString();
    }

    public Page<MediaFileResponse> listByCommunity(Long communityId, Pageable pageable) {
        return repository.findByCommunityIdAndStatusNot(communityId, FileStatus.HARD_DELETED, pageable)
                .map(this::toResponse);
    }

    public Page<MediaFileResponse> listByOwner(Long communityId, OwnerType ownerType,
                                                Long ownerId, Pageable pageable) {
        return repository.findByCommunityIdAndOwnerTypeAndOwnerIdAndStatusNot(
                communityId, ownerType, ownerId, FileStatus.HARD_DELETED, pageable)
                .map(this::toResponse);
    }

    public Page<MediaFileResponse> listByMediaType(Long communityId, MediaType mediaType,
                                                    Pageable pageable) {
        return repository.findByCommunityIdAndMediaTypeAndStatusNot(
                communityId, mediaType, FileStatus.HARD_DELETED, pageable)
                .map(this::toResponse);
    }

    // ─── Delete ──────────────────────────────────────────────────────────────

    @Transactional
    public void softDelete(Long id, Long communityId) {
        MediaFile file = repository
                .findByIdAndCommunityIdAndStatusNot(id, communityId, FileStatus.HARD_DELETED)
                .orElseThrow(() -> new MediaException("Media file not found: " + id));

        repository.softDelete(id, FileStatus.SOFT_DELETED, Instant.now());
        log.info("Soft-deleted media id={} s3Key={}", id, file.getS3Key());
    }

    @Transactional
    public void hardDelete(Long id, Long communityId) {
        MediaFile file = repository
                .findByIdAndCommunityIdAndStatusNot(id, communityId, FileStatus.HARD_DELETED)
                .orElseThrow(() -> new MediaException("Media file not found: " + id));

        String bucket = bucketFor(file.getAccessLevel());
        s3Service.deleteObject(bucket, file.getS3Key());

        if (file.getThumbnailS3Key() != null) {
            s3Service.deleteObject(bucket, file.getThumbnailS3Key());
        }
        repository.softDelete(id, FileStatus.HARD_DELETED, Instant.now());
        log.info("Hard-deleted media id={} s3Key={}", id, file.getS3Key());
    }

    // ─── Storage Usage ────────────────────────────────────────────────────────

    public long getStorageUsageBytes(Long communityId) {
        Long total = repository.sumActiveSizeBytesByCommunity(communityId);
        return total != null ? total : 0L;
    }

    // ─── Internal Helpers ─────────────────────────────────────────────────────

    @Async
    protected void processThumbnailAsync(byte[] bytes, String originalKey,
                                          String bucket, Long communityId,
                                          MediaFile.MediaFileBuilder builder) {
        imageService.generateThumbnail(bytes).ifPresent(thumbBytes -> {
            String thumbKey = originalKey.replace(".", "_thumb.");
            s3Service.uploadBytes(bucket, thumbKey, thumbBytes, "image/jpeg", null, null);
            String thumbCdnUrl = bucket.equals(s3Props.bucketPublic())
                    ? s3Service.buildCdnUrl(thumbKey) : null;
            // Update persisted record
            repository.findByS3Key(originalKey).ifPresent(mf -> {
                mf.setThumbnailS3Key(thumbKey);
                mf.setThumbnailCdnUrl(thumbCdnUrl);
                repository.save(mf);
                log.debug("Saved thumbnail for s3Key={}", originalKey);
            });
        });
    }

    private String buildS3Key(OwnerType ownerType, Long ownerId, MediaType mediaType,
                               String originalName, AccessLevel level) {
        String prefix    = level == AccessLevel.PUBLIC ? "public" : "private";
        String folder    = ownerType.name().toLowerCase() + "/" + Optional.ofNullable(ownerId).map(Object::toString).orElse("shared");
        String typePath  = mediaType.name().toLowerCase();
        String uuid      = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String ext       = extractExtension(originalName);
        return String.format("%s/%s/%s/%s%s", prefix, folder, typePath, uuid, ext);
    }

    private String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }

    private String bucketFor(AccessLevel level) {
        return level == AccessLevel.PUBLIC ? s3Props.bucketPublic() : s3Props.bucketPrivate();
    }

    private String detectMimeType(byte[] bytes, String filename) {
        try {
            return tika.detect(bytes, filename);
        } catch (Exception e) {
            return "application/octet-stream";
        }
    }

    private void validateMimeType(String mimeType) {
        if (!s3Props.effectiveMimeTypes().contains(mimeType)) {
            throw new MediaException("Unsupported file type: " + mimeType);
        }
    }

    private void validateSize(long sizeBytes) {
        if (sizeBytes > s3Props.maxFileSizeBytes()) {
            throw new MediaException("File exceeds maximum allowed size of "
                    + s3Props.maxFileSizeBytes() / (1024 * 1024) + " MB");
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new MediaException("Failed to read uploaded file: " + e.getMessage());
        }
    }

    private MediaFileResponse toResponse(MediaFile f) {
        return new MediaFileResponse(
                f.getId(), f.getCommunityId(), f.getOwnerType(), f.getOwnerId(),
                f.getOriginalName(), f.getS3Key(), f.getMimeType(), f.getSizeBytes(),
                f.getMediaType(), f.getAccessLevel(), f.getCdnUrl(),
                f.getWidthPx(), f.getHeightPx(), f.getThumbnailCdnUrl(),
                f.getStatus(), f.getModerationStatus(), f.getRetentionTier(),
                f.getCreatedAt(), f.getUpdatedAt()
        );
    }
}
