package com.manacommunity.media.service;

import com.manacommunity.media.domain.entity.MediaFile;
import com.manacommunity.media.domain.entity.MediaFile.*;
import com.manacommunity.media.domain.repository.MediaFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Automates multi-tier data lifecycle policies and garbage collection:
 * - Purges orphaned unconfirmed pre-signed uploads (older than 24h)
 * - Transitions expired files to COLD_STORAGE / ARCHIVED
 * - Deletes binaries of soft-deleted files beyond retention period
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaLifecycleService {

    private final MediaFileRepository repository;
    private final S3StorageService s3Service;

    /**
     * Run daily at 02:00 UTC to clean up unconfirmed pre-signed uploads.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public int cleanupPendingUploads() {
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        List<MediaFile> pending = repository.findByStatusAndCreatedAtBefore(FileStatus.PENDING_UPLOAD, cutoff);
        log.info("Found {} pending uploads older than 24h to clean up", pending.size());
        int count = 0;
        for (MediaFile file : pending) {
            file.setStatus(FileStatus.HARD_DELETED);
            repository.save(file);
            count++;
        }
        return count;
    }

    /**
     * Run daily at 03:00 UTC to transition expired files to COLD_STORAGE or ARCHIVED tier.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public int transitionExpiredTiers() {
        Instant now = Instant.now();
        List<MediaFile> expiredStandard = repository.findByRetentionTierAndRetentionExpiresAtBefore(RetentionTier.STANDARD, now);
        log.info("Found {} standard tier media files eligible for archival", expiredStandard.size());
        int count = 0;
        for (MediaFile file : expiredStandard) {
            file.setRetentionTier(RetentionTier.ARCHIVED);
            repository.save(file);
            count++;
        }
        return count;
    }

    /**
     * Run weekly to permanently erase binaries of soft-deleted files past 30 days.
     */
    @Scheduled(cron = "0 0 4 * * SUN")
    @Transactional
    public int purgeSoftDeletedBinaries() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        List<MediaFile> deleted = repository.findByStatusAndDeletedAtBefore(FileStatus.SOFT_DELETED, cutoff);
        log.info("Found {} soft-deleted media files past 30 days for permanent purge", deleted.size());
        int purged = 0;
        for (MediaFile file : deleted) {
            try {
                s3Service.deleteObject(file.getS3Key());
                if (file.getThumbnailS3Key() != null) {
                    s3Service.deleteObject(file.getThumbnailS3Key());
                }
                file.setStatus(FileStatus.HARD_DELETED);
                repository.save(file);
                purged++;
            } catch (Exception e) {
                log.error("Failed to purge binary for media file id={}: {}", file.getId(), e.getMessage());
            }
        }
        return purged;
    }
}