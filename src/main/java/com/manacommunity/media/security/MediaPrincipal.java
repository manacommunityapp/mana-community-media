package com.manacommunity.media.security;

import lombok.Getter;

/**
 * Authenticated principal stored in SecurityContext.
 * Carries userId, communityId (tenant), and email for request-level context.
 */
@Getter
public class MediaPrincipal {

    private final Long userId;
    private final Long communityId;
    private final String email;

    public MediaPrincipal(Long userId, Long communityId, String email) {
        this.userId      = userId;
        this.communityId = communityId;
        this.email       = email;
    }

    @Override
    public String toString() {
        return "MediaPrincipal{userId=" + userId + ", communityId=" + communityId + ", email=" + email + "}";
    }
}
