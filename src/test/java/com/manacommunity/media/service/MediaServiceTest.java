package com.manacommunity.media.service;

import com.manacommunity.media.config.S3Properties;
import com.manacommunity.media.domain.entity.MediaFile;
import com.manacommunity.media.domain.entity.MediaFile.*;
import com.manacommunity.media.domain.repository.MediaFileRepository;
import com.manacommunity.media.dto.*;
import com.manacommunity.media.exception.MediaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MediaService unit tests")
class MediaServiceTest {

    @Mock private S3StorageService         s3Service;
    @Mock private ImageProcessingService   imageService;
    @Mock private MediaFileRepository      repository;
    @Mock private S3Properties             s3Props;
    @org.mockito.Spy private org.apache.tika.Tika tika = new org.apache.tika.Tika();
    @InjectMocks private MediaService      mediaService;

    private static final Long COMMUNITY_ID = 1L;
    private static final Long USER_ID      = 10L;
    private static final Long OWNER_ID     = 42L;

    @BeforeEach
    void setUp() {
        when(s3Props.bucketPublic()).thenReturn("test-public");
        when(s3Props.bucketPrivate()).thenReturn("test-private");
        when(s3Props.cdnBaseUrl()).thenReturn("https://cdn.test.mana.community");
        when(s3Props.maxFileSizeBytes()).thenReturn(52_428_800L);
        when(s3Props.effectiveMimeTypes()).thenReturn(
                List.of("image/jpeg", "image/png", "application/pdf"));
    }

    @Test
    @DisplayName("uploadFile: persists PUBLIC media record with CDN URL")
    void uploadFile_public_createsCdnUrl() {
        byte[] fakeBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01};

        MockMultipartFile mockFile = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", fakeBytes);

        UploadRequest req = new UploadRequest(
                COMMUNITY_ID, OwnerType.USER, OWNER_ID, MediaType.AVATAR, AccessLevel.PUBLIC);

        when(s3Service.uploadBytes(anyString(), anyString(), any(), anyString(), any(), any()))
                .thenReturn("\"test-etag-123\"");
        when(s3Service.buildCdnUrl(anyString())).thenReturn("https://cdn.test.mana.community/public/user/42/avatar/abc123.jpg");
        when(imageService.isImage(anyString())).thenReturn(true);
        when(imageService.getDimensions(any())).thenReturn(new int[]{800, 600});
        when(repository.save(any(MediaFile.class))).thenAnswer(inv -> {
            MediaFile mf = inv.getArgument(0);
            mf.setId(1L);
            return mf;
        });

        MediaFileResponse response = mediaService.uploadFile(mockFile, req, COMMUNITY_ID, USER_ID);

        assertThat(response).isNotNull();
        assertThat(response.communityId()).isEqualTo(COMMUNITY_ID);
        assertThat(response.accessLevel()).isEqualTo(AccessLevel.PUBLIC);
        assertThat(response.cdnUrl()).isNotNull().startsWith("https://cdn");
        assertThat(response.widthPx()).isEqualTo(800);
        assertThat(response.heightPx()).isEqualTo(600);
        verify(s3Service).uploadBytes(eq("test-public"), anyString(), any(), anyString(), any(), any());
        verify(repository).save(any(MediaFile.class));
    }

    @Test
    @DisplayName("uploadFile: PRIVATE media has null CDN URL")
    void uploadFile_private_nullCdnUrl() {
        byte[] fakeBytes = "%PDF-1.4\n%trailer\n%%EOF".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile mockFile = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", fakeBytes);

        UploadRequest req = new UploadRequest(
                COMMUNITY_ID, OwnerType.USER, OWNER_ID, MediaType.DOCUMENT, AccessLevel.PRIVATE);

        when(s3Service.uploadBytes(anyString(), anyString(), any(), anyString(), any(), any()))
                .thenReturn("\"etag-456\"");
        when(imageService.isImage("application/pdf")).thenReturn(false);
        when(repository.save(any(MediaFile.class))).thenAnswer(inv -> {
            MediaFile mf = inv.getArgument(0);
            mf.setId(2L);
            return mf;
        });

        MediaFileResponse response = mediaService.uploadFile(mockFile, req, COMMUNITY_ID, USER_ID);

        assertThat(response.cdnUrl()).isNull();
        assertThat(response.accessLevel()).isEqualTo(AccessLevel.PRIVATE);
        verify(s3Service).uploadBytes(eq("test-private"), anyString(), any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("getFileById: throws MediaException when not found")
    void getFileById_notFound_throws() {
        when(repository.findByIdAndCommunityIdAndStatusNot(anyLong(), anyLong(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> mediaService.getFileById(999L, COMMUNITY_ID))
                .isInstanceOf(MediaException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("getAccessUrl: returns CDN URL for PUBLIC files")
    void getAccessUrl_public_returnsCdnUrl() {
        MediaFile file = MediaFile.builder()
                .id(1L).communityId(COMMUNITY_ID)
                .accessLevel(AccessLevel.PUBLIC)
                .cdnUrl("https://cdn.test.mana.community/public/avatar.jpg")
                .status(FileStatus.ACTIVE)
                .build();

        when(repository.findByIdAndCommunityIdAndStatusNot(1L, COMMUNITY_ID, FileStatus.HARD_DELETED))
                .thenReturn(Optional.of(file));

        String url = mediaService.getAccessUrl(1L, COMMUNITY_ID);

        assertThat(url).isEqualTo("https://cdn.test.mana.community/public/avatar.jpg");
        verify(s3Service, never()).generatePresignedGetUrl(anyString(), anyString());
    }

    @Test
    @DisplayName("softDelete: marks file as SOFT_DELETED")
    void softDelete_marksStatus() {
        MediaFile file = MediaFile.builder()
                .id(1L).communityId(COMMUNITY_ID)
                .s3Key("public/user/1/avatar/abc.jpg")
                .status(FileStatus.ACTIVE)
                .build();

        when(repository.findByIdAndCommunityIdAndStatusNot(1L, COMMUNITY_ID, FileStatus.HARD_DELETED))
                .thenReturn(Optional.of(file));
        when(repository.softDelete(anyLong(), any(), any(Instant.class))).thenReturn(1);

        mediaService.softDelete(1L, COMMUNITY_ID);

        verify(repository).softDelete(eq(1L), eq(FileStatus.SOFT_DELETED), any(Instant.class));
    }
}
