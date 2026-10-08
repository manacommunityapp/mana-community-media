package com.manacommunity.media.service;

import com.manacommunity.media.domain.entity.MediaFile;
import com.manacommunity.media.domain.entity.MediaFile.*;
import com.manacommunity.media.domain.repository.MediaFileRepository;
import com.manacommunity.media.dto.MediaFileResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MediaModerationService unit tests")
class MediaModerationServiceTest {

    @Mock private MediaFileRepository repository;
    @Mock private S3StorageService s3Service;
    @InjectMocks private MediaModerationService moderationService;

    @Test
    @DisplayName("assessContent: flags suspicious executable extensions")
    void assessContent_executable_quarantined() {
        var assessment = moderationService.assessContent(
                new byte[]{1, 2, 3}, "malicious_script.sh", "text/plain");

        assertThat(assessment.status()).isEqualTo(ModerationStatus.QUARANTINED);
        assertThat(assessment.flags()).contains("SUSPICIOUS_EXECUTABLE_EXT");
        assertThat(assessment.safetyScore()).isGreaterThanOrEqualTo(0.8);
    }

    @Test
    @DisplayName("assessContent: approves standard image")
    void assessContent_standardImage_approved() {
        var assessment = moderationService.assessContent(
                new byte[]{1, 2, 3, 4, 5}, "my_profile.jpg", "image/jpeg");

        assertThat(assessment.status()).isEqualTo(ModerationStatus.APPROVED);
        assertThat(assessment.flags()).isEmpty();
    }

    @Test
    @DisplayName("approveMedia: updates status to APPROVED")
    void approveMedia_success() {
        MediaFile file = MediaFile.builder()
                .id(100L)
                .communityId(1L)
                .s3Key("public/test.jpg")
                .originalName("test.jpg")
                .ownerType(OwnerType.USER)
                .mediaType(MediaType.AVATAR)
                .accessLevel(AccessLevel.PUBLIC)
                .status(FileStatus.ACTIVE)
                .moderationStatus(ModerationStatus.FLAGGED)
                .createdAt(Instant.now())
                .build();

        when(repository.findById(100L)).thenReturn(Optional.of(file));
        when(repository.save(any(MediaFile.class))).thenAnswer(inv -> inv.getArgument(0));

        MediaFileResponse resp = moderationService.approveMedia(100L, 999L);

        assertThat(resp.moderationStatus()).isEqualTo(ModerationStatus.APPROVED);
        verify(repository).save(file);
    }

    @Test
    @DisplayName("quarantineMedia: sets status to QUARANTINED and soft deletes")
    void quarantineMedia_success() {
        MediaFile file = MediaFile.builder()
                .id(200L)
                .communityId(1L)
                .s3Key("public/bad.jpg")
                .originalName("bad.jpg")
                .ownerType(OwnerType.FEED)
                .mediaType(MediaType.GALLERY_IMAGE)
                .accessLevel(AccessLevel.PUBLIC)
                .status(FileStatus.ACTIVE)
                .moderationStatus(ModerationStatus.APPROVED)
                .createdAt(Instant.now())
                .build();

        when(repository.findById(200L)).thenReturn(Optional.of(file));
        when(repository.save(any(MediaFile.class))).thenAnswer(inv -> inv.getArgument(0));

        MediaFileResponse resp = moderationService.quarantineMedia(200L, 999L, "TOS_VIOLATION");

        assertThat(resp.moderationStatus()).isEqualTo(ModerationStatus.QUARANTINED);
        assertThat(file.getStatus()).isEqualTo(FileStatus.SOFT_DELETED);
    }
}