package com.manacommunity.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT configuration for validating tokens issued by the auth service.
 */
@ConfigurationProperties(prefix = "media.jwt")
public record JwtProperties(
        String secret,          // HMAC-SHA256 secret shared with auth service
        String issuer           // expected issuer claim
) {}
