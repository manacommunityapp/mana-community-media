package com.manacommunity.media.service;

import com.manacommunity.media.config.S3Properties;
import com.manacommunity.media.domain.entity.MediaFile;
import com.manacommunity.media.domain.entity.MediaFile.FileStatus;
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
 * Scheduled cleanup jobs for the media service lifecycle.
 *
 * <ul>
 *   <li>Purge soft-deleted files from S3 after a retention period (default 30 days).</li>
 *   <li>Remove orphaned PENDING_UPLOAD records where the client never completed upload.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaCleanupService {

    private final MediaFileRepository repository;
    private final S3StorageService     s3Service;
    private final S3Properties         props;

    private static final int SOFT_DELETE_RETENTION_DAYS = 30;
    private static final int PENDING_UPLOAD_TTL_HOURS   = 2;

    /**
     * Runs daily at 02:00 UTC.
     * Hard-deletes S3 objects that were soft-deleted more than 30 days ago.
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    @Transactional
    public void purgeSoftDeletedFiles() {
        Instant cutoff = Instant.now().minus(SOFT_DELETE_RETENTION_DAYS, ChronoUnit.DAYS);
        List<MediaFile> toDelete = repository.findByStatusAndDeletedAtBefore(
                FileStatus.SOFT_DELETED, cutoff);

        log.info("Purge job: found {} soft-deleted files older than {} days", toDelete.size(), SOFT_DELETE_RETENTION_DAYS);
        int purged = 0;

        for (MediaFile file : toDelete) {
            try {
                String bucket = file.getAccessLevel() == MediaFile.AccessLevel.PUBLIC
                        ? props.bucketPublic() : props.bucketPrivate();

                s3Service.deleteObject(bucket, file.getS3Key());

                if (file.getThumbnailS3Key() != null) {
                    s3Service.deleteObject(bucket, file.getThumbnailS3Key());
                }
                repository.softDelete(file.getId(), FileStatus.HARD_DELETED, Instant.now());
                purged++;
            } catch (Exception e) {
                log.error("Failed to hard-delete media id={} s3Key={}: {}", file.getId(), file.getS3Key(), e.getMessage());
            }
        }
        log.info("Purge job complete: {} files hard-deleted from S3 and DB", purged);
    }

    /**
     * Runs every hour.
     * Marks PENDING_UPLOAD records as SOFT_DELETED if the client never confirmed upload.
     */
    @Scheduled(fixedDelay = 3_600_000L)
    @Transactional
    public void cleanOrphanedPendingUploads() {
        Instant cutoff = Instant.now().minus(PENDING_UPLOAD_TTL_HOURS, ChronoUnit.HOURS);
        List<MediaFile> orphans = repository.findByStatusAndDeletedAtBefore(
                FileStatus.PENDING_UPLOAD, cutoff);

        if (!orphans.isEmpty()) {
            log.info("Cleanup: marking {} orphaned PENDING_UPLOAD records as SOFT_DELETED", orphans.size());
            orphans.forEach(file ->
                    repository.softDelete(file.getId(), FileStatus.SOFT_DELETED, Instant.now()));
        }
    }
}
