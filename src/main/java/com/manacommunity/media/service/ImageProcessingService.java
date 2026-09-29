package com.manacommunity.media.service;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

/**
 * Server-side image processing using Thumbnailator.
 * <p>
 * Provides resizing, thumbnail generation, and format conversion.
 */
@Slf4j
@Service
public class ImageProcessingService {

    private static final int THUMBNAIL_WIDTH  = 300;
    private static final int THUMBNAIL_HEIGHT = 300;
    private static final String OUTPUT_FORMAT = "jpg";

    /**
     * Generates a square thumbnail from raw image bytes.
     *
     * @param originalBytes raw image file bytes
     * @return JPEG thumbnail bytes, or empty if processing fails
     */
    public Optional<byte[]> generateThumbnail(byte[] originalBytes) {
        try (ByteArrayInputStream in  = new ByteArrayInputStream(originalBytes);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Thumbnails.of(in)
                    .crop(Positions.CENTER)
                    .size(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
                    .outputFormat(OUTPUT_FORMAT)
                    .outputQuality(0.85)
                    .toOutputStream(out);

            return Optional.of(out.toByteArray());
        } catch (IOException e) {
            log.warn("Thumbnail generation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Resizes an image to fit within maxWidth × maxHeight (preserving aspect ratio).
     */
    public Optional<byte[]> resize(byte[] originalBytes, int maxWidth, int maxHeight) {
        try (ByteArrayInputStream in  = new ByteArrayInputStream(originalBytes);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Thumbnails.of(in)
                    .size(maxWidth, maxHeight)
                    .keepAspectRatio(true)
                    .outputFormat(OUTPUT_FORMAT)
                    .outputQuality(0.88)
                    .toOutputStream(out);

            return Optional.of(out.toByteArray());
        } catch (IOException e) {
            log.warn("Image resize failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Returns image dimensions from raw bytes.
     */
    public int[] getDimensions(byte[] imageBytes) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(imageBytes)) {
            BufferedImage image = ImageIO.read(in);
            if (image != null) {
                return new int[]{image.getWidth(), image.getHeight()};
            }
        } catch (IOException e) {
            log.warn("Could not read image dimensions: {}", e.getMessage());
        }
        return new int[]{0, 0};
    }

    /**
     * Returns true if the MIME type represents an image.
     */
    public boolean isImage(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }

    /**
     * Returns true if the MIME type represents a video.
     */
    public boolean isVideo(String mimeType) {
        return mimeType != null && mimeType.startsWith("video/");
    }
}
