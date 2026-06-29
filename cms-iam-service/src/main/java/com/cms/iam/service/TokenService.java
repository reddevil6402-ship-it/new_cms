package com.cms.iam.service;

import com.cms.iam.domain.Permission;
import com.cms.iam.domain.RefreshToken;
import com.cms.iam.domain.User;
import com.cms.iam.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${cms.jwt.access-token-ttl-seconds:900}")
    private long accessTokenTtlSeconds;

    @Value("${cms.jwt.refresh-token-ttl-days:7}")
    private long refreshTokenTtlDays;

    @Value("${cms.jwt.key-id:cms-key-v1}")
    private String keyId;

    /**
     * Issues a signed RS256 JWT access token with the full permission set embedded.
     * See auth-flow.md § 2.3 for the full claims specification.
     */
    public String issueAccessToken(User user) {
        Instant now = Instant.now();

        List<String> permissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::toPermissionString)
                .distinct()
                .toList();

        List<String> roles = user.getRoles().stream()
                .map(r -> r.getCode())
                .distinct()
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("cms-iam-service")
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(accessTokenTtlSeconds))
                .claim("jti",          java.util.UUID.randomUUID().toString())
                .claim("tenantId",     user.getTenant().getId().toString())
                .claim("tenantCode",   user.getTenant().getCode())
                .claim("tenantStatus", user.getTenant().getStatus().name())
                .claim("userId",       user.getId().toString())
                .claim("email",        user.getEmail())
                .claim("fullName",     user.getFullName())
                .claim("roles",        roles)
                .claim("permissions",  permissions)
                .build();

        JwsHeader header = JwsHeader.with(() -> "RS256")
                .keyId(keyId)
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * Generates, persists, and returns a new opaque refresh token.
     * The raw token is returned to the caller (for the cookie).
     * Only the SHA-256 hash is stored in the database.
     *
     * <p>Multi-device: does NOT revoke existing refresh tokens. New token is inserted.
     * Locked decision from auth-flow.md § 11, question 4.
     */
    @Transactional
    public String issueRefreshToken(User user, String ipAddress, String userAgent) {
        String rawToken = generateSecureToken();
        String tokenHash = sha256Hex(rawToken);

        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setTokenHash(tokenHash);
        entity.setExpiresAt(OffsetDateTime.now().plusDays(refreshTokenTtlDays));
        entity.setIpAddress(ipAddress);
        entity.setUserAgent(userAgent);

        refreshTokenRepository.save(entity);
        return rawToken;
    }

    /**
     * Validates the incoming refresh token hash, revokes it (one-time use),
     * and returns the associated User if valid.
     *
     * @return the User associated with the token
     * @throws com.cms.common.exception.CmsException if token is invalid/expired/revoked
     */
    @Transactional
    public RefreshToken validateAndRevokeRefreshToken(String rawToken) {
        String hash = sha256Hex(rawToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new com.cms.common.exception.CmsException(
                        com.cms.common.exception.ErrorCode.REFRESH_TOKEN_INVALID));

        if (!token.isValid()) {
            throw new com.cms.common.exception.CmsException(
                    com.cms.common.exception.ErrorCode.REFRESH_TOKEN_INVALID);
        }

        // Revoke the old token (one-time use rotation)
        token.setRevokedAt(OffsetDateTime.now());
        refreshTokenRepository.save(token);

        return token;
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String generateSecureToken() {
        byte[] bytes = new byte[32];   // 256 bits of randomness
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
