package com.manacommunity.media.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Persistent record of every uploaded file.
 * <p>
 * The actual binary is stored in S3; this table only holds metadata and the S3 object key
 * needed to serve or manage the file later.
 */
@Entity
@Table(name = "media_files", indexes = {
        @Index(name = "idx_media_owner",        columnList = "owner_type, owner_id"),
        @Index(name = "idx_media_community",    columnList = "community_id"),
        @Index(name = "idx_media_access_level", columnList = "access_level"),
        @Index(name = "idx_media_media_type",   columnList = "media_type"),
        @Index(name = "idx_media_status",       columnList = "status"),
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Community this file belongs to (multi-tenant isolation). */
    @Column(name = "community_id", nullable = false)
    private Long communityId;

    /** Logical owner type: USER, TOURNAMENT, EVENT, COMMUNITY, etc. */
    @Column(name = "owner_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private OwnerType ownerType;

    /** ID of the owning entity (userId, tournamentId, eventId …). */
    @Column(name = "owner_id")
    private Long ownerId;

    /** Original filename as submitted by the client. */
    @Column(name = "original_name", nullable = false)
    private String originalName;

    /** S3 object key (e.g. "public/avatars/u_102.webp"). */
    @Column(name = "s3_key", nullable = false, unique = true)
    private String s3Key;

    /** MIME type detected server-side (e.g. "image/webp"). */
    @Column(name = "mime_type", length = 100)
    private String mimeType;

    /** Size of the stored object in bytes. */
    @Column(name = "size_bytes")
    private Long sizeBytes;

    /** Logical media category. */
    @Column(name = "media_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private MediaType mediaType;

    /** PUBLIC (CDN URL) or PRIVATE (requires pre-signed URL). */
    @Column(name = "access_level", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AccessLevel accessLevel;

    /**
     * For PUBLIC assets: the permanent CDN URL returned to clients.
     * For PRIVATE assets: null — a pre-signed URL is generated on demand.
     */
    @Column(name = "cdn_url", length = 1024)
    private String cdnUrl;

    /** Image width in pixels (null for non-images). */
    @Column(name = "width_px")
    private Integer widthPx;

    /** Image height in pixels (null for non-images). */
    @Column(name = "height_px")
    private Integer heightPx;

    /** S3 key of the auto-generated thumbnail (images only). */
    @Column(name = "thumbnail_s3_key")
    private String thumbnailS3Key;

    /** Public CDN URL of the thumbnail (null for private or non-image). */
    @Column(name = "thumbnail_cdn_url", length = 1024)
    private String thumbnailCdnUrl;

    /** Current lifecycle status of this file. */
    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private FileStatus status = FileStatus.ACTIVE;

    /** S3 version ID for versioned buckets (optional). */
    @Column(name = "s3_version_id", length = 256)
    private String s3VersionId;

    /** ETag returned by S3 after upload. */
    @Column(name = "etag", length = 256)
    private String etag;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** Soft-delete timestamp. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    // ─── Enumerations ────────────────────────────────────────────────────────

    public enum OwnerType {
        USER, COMMUNITY, TOURNAMENT, EVENT, POST, ANNOUNCEMENT, TEAM
    }

    public enum MediaType {
        AVATAR, BANNER, GALLERY_IMAGE, DOCUMENT, VIDEO, ATTACHMENT, LOGO, THUMBNAIL
    }

    public enum AccessLevel {
        PUBLIC, PRIVATE
    }

    public enum FileStatus {
        PENDING_UPLOAD, ACTIVE, SOFT_DELETED, HARD_DELETED
    }
}
