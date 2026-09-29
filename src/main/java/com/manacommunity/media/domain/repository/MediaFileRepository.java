package com.manacommunity.media.domain.repository;

import com.manacommunity.media.domain.entity.MediaFile;
import com.manacommunity.media.domain.entity.MediaFile.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MediaFileRepository extends JpaRepository<MediaFile, Long> {

    Optional<MediaFile> findByIdAndCommunityIdAndStatusNot(Long id, Long communityId, FileStatus status);

    Optional<MediaFile> findByS3Key(String s3Key);

    Page<MediaFile> findByCommunityIdAndStatusNot(Long communityId, FileStatus status, Pageable pageable);

    Page<MediaFile> findByCommunityIdAndOwnerTypeAndOwnerIdAndStatusNot(
            Long communityId, OwnerType ownerType, Long ownerId, FileStatus status, Pageable pageable);

    Page<MediaFile> findByCommunityIdAndMediaTypeAndStatusNot(
            Long communityId, MediaType mediaType, FileStatus status, Pageable pageable);

    List<MediaFile> findByOwnerTypeAndOwnerIdAndMediaTypeAndStatusNot(
            OwnerType ownerType, Long ownerId, MediaType mediaType, FileStatus status);

    @Modifying
    @Query("UPDATE MediaFile f SET f.status = :status, f.deletedAt = :deletedAt WHERE f.id = :id")
    int softDelete(@Param("id") Long id, @Param("status") FileStatus status,
                   @Param("deletedAt") Instant deletedAt);

    @Query("SELECT SUM(f.sizeBytes) FROM MediaFile f WHERE f.communityId = :communityId AND f.status = 'ACTIVE'")
    Long sumActiveSizeBytesByCommunity(@Param("communityId") Long communityId);

    List<MediaFile> findByStatusAndDeletedAtBefore(FileStatus status, Instant before);
}
