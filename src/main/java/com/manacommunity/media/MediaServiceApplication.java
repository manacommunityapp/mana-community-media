package com.manacommunity.media;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Mana Community Media Service
 *
 * <p>Microservice responsible for:
 * <ul>
 *   <li>File upload via direct multipart or S3 pre-signed PUT URLs</li>
 *   <li>Public CDN asset serving (avatars, banners, tournament media)</li>
 *   <li>Private asset access via time-limited S3 pre-signed GET URLs</li>
 *   <li>Image resizing and thumbnail generation</li>
 *   <li>Media metadata persistence (PostgreSQL)</li>
 *   <li>File deletion and lifecycle management</li>
 * </ul>
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
@ConfigurationPropertiesScan
public class MediaServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediaServiceApplication.class, args);
    }
}
