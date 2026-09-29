# Mana Community Media Service — Walkthrough

## Overview
A dedicated Spring Boot 4 microservice for all binary media storage in the Mana Community platform.
Runs on **port 8084**, uses **AWS S3** for binary storage, and **PostgreSQL** for metadata.

---

## Project Structure

```
mana-community-media/
├── pom.xml                                     # Spring Boot 4.0.5, AWS SDK v2, Thumbnailator, Tika
├── Dockerfile                                  # Multi-stage build (JDK 17 alpine → JRE runtime)
├── docker-compose.yml                          # Local dev: media service + PostgreSQL 16
├── .gitignore
└── src/
    ├── main/
    │   ├── java/com/manacommunity/media/
    │   │   ├── MediaServiceApplication.java    # @SpringBootApplication entry point
    │   │   ├── config/
    │   │   │   ├── AppConfig.java              # Apache Tika bean + async thread pool
    │   │   │   ├── AwsConfig.java              # S3Client, S3Presigner, S3TransferManager
    │   │   │   ├── JwtProperties.java          # JWT config record
    │   │   │   ├── S3Properties.java           # S3/CDN config record
    │   │   │   └── SecurityConfig.java         # Stateless JWT security + CORS
    │   │   ├── controller/
    │   │   │   └── MediaController.java        # REST API (upload, access, list, delete)
    │   │   ├── domain/
    │   │   │   ├── entity/MediaFile.java       # JPA entity with nested enums
    │   │   │   └── repository/MediaFileRepository.java
    │   │   ├── dto/
    │   │   │   ├── UploadRequest.java
    │   │   │   ├── PresignedUploadRequest.java
    │   │   │   ├── PresignedUploadResponse.java
    │   │   │   ├── ConfirmUploadRequest.java
    │   │   │   └── MediaFileResponse.java
    │   │   ├── exception/
    │   │   │   ├── MediaException.java
    │   │   │   └── GlobalExceptionHandler.java # RFC 7807 ProblemDetail responses
    │   │   ├── security/
    │   │   │   ├── JwtAuthenticationFilter.java
    │   │   │   ├── JwtTokenValidator.java
    │   │   │   └── MediaPrincipal.java
    │   │   └── service/
    │   │       ├── S3StorageService.java       # Low-level S3 SDK wrapper
    │   │       ├── ImageProcessingService.java # Thumbnailator resize + thumbnail
    │   │       ├── MediaService.java           # Core business logic
    │   │       └── MediaCleanupService.java    # Scheduled purge jobs
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/
    │           └── V1__create_media_files_table.sql
    └── test/
        ├── java/com/manacommunity/media/service/
        │   ├── MediaServiceTest.java
        │   └── ImageProcessingServiceTest.java
        └── resources/application.yml
```

---

## Key Design Decisions

### 1. Two Upload Strategies

| Strategy | When to use | Flow |
|---|---|---|
| **Server-Mediated** `POST /api/media/files/upload` | Files < 10 MB, avatars | Client → Server → S3 |
| **Pre-signed PUT** `POST /api/media/files/presigned-url` | Files > 10 MB, videos | Client → Server (URL) → S3 direct → Server (confirm) |

### 2. Public vs Private Assets

| Access Level | S3 Bucket | URL Type | Use Case |
|---|---|---|---|
| `PUBLIC` | `mana-community-public` | Permanent CDN URL | Avatars, banners, event photos |
| `PRIVATE` | `mana-community-private` | Pre-signed GET (15 min TTL) | KYC docs, invoices, private attachments |

### 3. S3 Key Structure

```
public/user/{ownerId}/avatar/{uuid}.jpg
public/tournament/{ownerId}/banner/{uuid}.jpg
private/user/{ownerId}/document/{uuid}.pdf
```

### 4. Image Processing
- Auto-generates **300x300 square thumbnails** async (non-blocking)
- Thumbnail stored under `_thumb` key suffix in same bucket
- Thumbnail CDN URL returned in `thumbnailCdnUrl` response field

---

## REST API Reference

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/media/files/upload` | Multipart upload (server-mediated) |
| `POST` | `/api/media/files/presigned-url` | Get pre-signed S3 PUT URL |
| `POST` | `/api/media/files/confirm` | Confirm direct S3 upload |
| `GET` | `/api/media/files/{id}` | Get file metadata |
| `GET` | `/api/media/files/{id}/access` | Get CDN or pre-signed GET URL |
| `GET` | `/api/media/files` | List files for community (paginated) |
| `GET` | `/api/media/files/by-owner` | Filter by owner type + ID |
| `GET` | `/api/media/files/by-type` | Filter by media type |
| `GET` | `/api/media/files/storage-usage` | Storage usage in bytes/KB/MB |
| `DELETE` | `/api/media/files/{id}` | Soft delete (30-day retention) |
| `DELETE` | `/api/media/files/{id}/hard` | Hard delete from S3 + DB |

---

## Environment Variables

```env
DB_URL=jdbc:postgresql://localhost:5432/mana_media
DB_USER=postgres
DB_PASSWORD=postgres

JWT_SECRET=<shared secret with auth service>
JWT_ISSUER=mana-community-auth

AWS_REGION=ap-south-1
AWS_ACCESS_KEY_ID=<your key>
AWS_SECRET_ACCESS_KEY=<your secret>

S3_BUCKET_PUBLIC=mana-community-public
S3_BUCKET_PRIVATE=mana-community-private
CDN_BASE_URL=https://cdn.mana.community

MEDIA_MAX_FILE_SIZE=50MB
```

---

## Scheduled Jobs

| Job | Schedule | Action |
|---|---|---|
| `purgeSoftDeletedFiles` | Daily 02:00 UTC | Hard-deletes S3 objects soft-deleted > 30 days |
| `cleanOrphanedPendingUploads` | Every hour | Marks stale PENDING_UPLOAD as SOFT_DELETED |

---

## Running Locally

```bash
# Start DB + service via Docker Compose
docker-compose up -d

# Or run only the service (needs local PostgreSQL on 5432)
mvn spring-boot:run

# Swagger UI
http://localhost:8084/swagger-ui.html
```

---

## Dependencies

| Library | Purpose |
|---|---|
| `spring-boot-starter-web` | REST API |
| `spring-boot-starter-data-jpa` | PostgreSQL persistence |
| `spring-boot-starter-security` | Stateless JWT auth |
| `software.amazon.awssdk:s3` | S3 upload, delete, pre-signing |
| `software.amazon.awssdk:s3-presigner` | Pre-signed URL generation |
| `net.coobird:thumbnailator` | Image resizing + thumbnail generation |
| `org.apache.tika:tika-core` | Server-side MIME type detection |
| `io.jsonwebtoken:jjwt-*` | JWT token validation |
| `org.flywaydb` | Database schema migrations |
| `springdoc-openapi` | Swagger / OpenAPI docs |
