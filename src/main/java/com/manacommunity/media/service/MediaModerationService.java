package com.manacommunity.media.service;

import com.manacommunity.media.domain.entity.MediaFile;
import com.manacommunity.media.domain.entity.MediaFile.*;
import com.manacommunity.media.domain.repository.MediaFileRepository;
import com.manacommunity.media.dto.MediaFileResponse;
import com.manacommunity.media.exception.MediaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Moderation and content safety pipeline for all uploaded community media.
 * Supports automated heuristics/scoring, quarantine, and administrative reviews.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaModerationService {

    private final MediaFileRepository repository;
    private final S3StorageService s3Service;

    private static final Pattern SUSPICIOUS_EXTENSION = Pattern.compile("(?i)\\.(exe|sh|bat|cmd|vbs|js|php|py|jar|scr)$");
    private static final Pattern DOUBLE_EXTENSION = Pattern.compile("(?i)\\.[a-z0-9]+\\.(jpg|jpeg|png|webp|pdf)$");

    public record ModerationAssessment(
            ModerationStatus status,
            double safetyScore,
            String flags
    ) {}

    /**
     * Automated heuristic analysis on uploaded content.
     */
    public ModerationAssessment assessContent(byte[] bytes, String filename, String mimeType) {
        List<String> flags = new ArrayList<>();
        double toxicityScore = 0.0;

        if (filename != null) {
            if (SUSPICIOUS_EXTENSION.matcher(filename).find()) {
                flags.add("SUSPICIOUS_EXECUTABLE_EXT");
                toxicityScore += 0.9;
            }
            if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                flags.add("PATH_TRAVERSAL_IN_NAME");
                toxicityScore += 0.5;
            }
        }

        if (mimeType != null && mimeType.equalsIgnoreCase("application/x-msdownload")) {
            flags.add("DISALLOWED_BINARY_MIME");
            toxicityScore += 0.95;
        }

        // Check for empty or oversized payloads
        if (bytes == null || bytes.length == 0) {
            flags.add("EMPTY_PAYLOAD");
            toxicityScore += 0.3;
        }

        ModerationStatus status = ModerationStatus.APPROVED;
        if (toxicityScore >= 0.8) {
            status = ModerationStatus.QUARANTINED;
        } else if (toxicityScore >= 0.4 || !flags.isEmpty()) {
            status = ModerationStatus.FLAGGED;
        }

        return new ModerationAssessment(status, toxicityScore, String.join(",", flags));
    }

    /**
     * Retrieve queue of media items awaiting or undergoing moderation review.
     */
    public Page<MediaFileResponse> getModerationQueue(Long communityId, ModerationStatus status, Pageable pageable) {
        Page<MediaFile> page = communityId != null
                ? repository.findByCommunityIdAndModerationStatus(communityId, status, pageable)
                : repository.findByModerationStatus(status, pageable);
        return page.map(this::toResponse);
    }

    /**
     * Admin approval of flagged or quarantined content.
     */
    @Transactional
    public MediaFileResponse approveMedia(Long fileId, Long adminId) {
        MediaFile file = getFile(fileId);
        file.setModerationStatus(ModerationStatus.APPROVED);
        file.setModeratedBy(adminId);
        file.setModeratedAt(Instant.now());
        if (file.getStatus() == FileStatus.SOFT_DELETED && file.getDeletedAt() != null) {
            file.setStatus(FileStatus.ACTIVE);
            file.setDeletedAt(null);
        }
        log.info("Media file id={} APPROVED by admin={}", fileId, adminId);
        return toResponse(repository.save(file));
    }

    /**
     * Quarantine suspicious or reported media, making it inaccessible to end users.
     */
    @Transactional
    public MediaFileResponse quarantineMedia(Long fileId, Long adminId, String reason) {
        MediaFile file = getFile(fileId);
        file.setModerationStatus(ModerationStatus.QUARANTINED);
        file.setModerationFlags(reason != null ? reason : "ADMIN_QUARANTINED");
        file.setModeratedBy(adminId);
        file.setModeratedAt(Instant.now());
        file.setStatus(FileStatus.SOFT_DELETED);
        file.setDeletedAt(Instant.now());
        log.warn("Media file id={} QUARANTINED by admin={} reason={}", fileId, adminId, reason);
        return toResponse(repository.save(file));
    }

    /**
     * Permanently purge toxic media binary from storage and record.
     */
    @Transactional
    public void purgeMedia(Long fileId, Long adminId) {
        MediaFile file = getFile(fileId);
        log.warn("Media file id={} s3Key={} permanently PURGED by admin={}", fileId, file.getS3Key(), adminId);
        try {
            s3Service.deleteObject(file.getS3Key());
            if (file.getThumbnailS3Key() != null) {
                s3Service.deleteObject(file.getThumbnailS3Key());
            }
        } catch (Exception ex) {
            log.error("Failed to delete S3 binary for purged media id={}: {}", fileId, ex.getMessage());
        }
        file.setStatus(FileStatus.HARD_DELETED);
        file.setModerationStatus(ModerationStatus.REJECTED);
        file.setModeratedBy(adminId);
        file.setModeratedAt(Instant.now());
        file.setDeletedAt(Instant.now());
        repository.save(file);
    }

    private MediaFile getFile(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new MediaException("Media file not found: " + id));
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