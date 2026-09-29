package com.manacommunity.media.security;

import com.manacommunity.media.config.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Optional;

/**
 * Validates JWT access tokens issued by the auth service.
 * Extracts claims needed for authentication and tenant resolution.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenValidator {

    private final JwtProperties jwtProps;

    public Optional<Claims> validateAndExtract(String token) {
        try {
            Key key = Keys.hmacShaKeyFor(jwtProps.secret().getBytes(StandardCharsets.UTF_8));
            return Optional.of(
                    Jwts.parser()
                            .verifyWith((javax.crypto.SecretKey) key)
                            .requireIssuer(jwtProps.issuer())
                            .build()
                            .parseSignedClaims(token)
                            .getPayload()
            );
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("JWT validation failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<Long> extractUserId(String token) {
        return validateAndExtract(token)
                .map(c -> c.get("userId", Long.class));
    }

    public Optional<Long> extractCommunityId(String token) {
        return validateAndExtract(token)
                .map(c -> c.get("communityId", Long.class));
    }
}
