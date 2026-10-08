package com.manacommunity.media.controller;

import com.manacommunity.media.domain.entity.MediaFile.ModerationStatus;
import com.manacommunity.media.dto.MediaFileResponse;
import com.manacommunity.media.security.MediaPrincipal;
import com.manacommunity.media.service.MediaModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Media Moderation", description = "Administrative queue and content moderation review")
@RestController
@RequestMapping("/api/media/admin/moderation")
@RequiredArgsConstructor
public class MediaModerationController {

    private final MediaModerationService moderationService;

    @Operation(summary = "Get flagged or queued media files for moderation review")
    @GetMapping("/queue")
    public ResponseEntity<Page<MediaFileResponse>> getModerationQueue(
            @RequestParam(defaultValue = "FLAGGED") ModerationStatus status,
            @RequestParam(required = false) Long communityId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(moderationService.getModerationQueue(communityId, status, pageable));
    }

    @Operation(summary = "Approve a previously flagged or quarantined media file")
    @PostMapping("/{id}/approve")
    public ResponseEntity<MediaFileResponse> approveMedia(
            @PathVariable Long id,
            @AuthenticationPrincipal MediaPrincipal principal) {
        Long adminId = principal != null ? principal.getUserId() : 1L;
        return ResponseEntity.ok(moderationService.approveMedia(id, adminId));
    }

    @Operation(summary = "Quarantine suspicious or reported media")
    @PostMapping("/{id}/quarantine")
    public ResponseEntity<MediaFileResponse> quarantineMedia(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal MediaPrincipal principal) {
        Long adminId = principal != null ? principal.getUserId() : 1L;
        String reason = body != null ? body.get("reason") : "MANUAL_QUARANTINE";
        return ResponseEntity.ok(moderationService.quarantineMedia(id, adminId, reason));
    }

    @Operation(summary = "Permanently purge quarantined toxic media")
    @DeleteMapping("/{id}/purge")
    public ResponseEntity<Void> purgeMedia(
            @PathVariable Long id,
            @AuthenticationPrincipal MediaPrincipal principal) {
        Long adminId = principal != null ? principal.getUserId() : 1L;
        moderationService.purgeMedia(id, adminId);
        return ResponseEntity.noContent().build();
    }
}