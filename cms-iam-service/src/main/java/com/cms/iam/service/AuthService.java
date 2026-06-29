package com.cms.iam.service;

import com.cms.common.exception.CmsException;
import com.cms.common.exception.ErrorCode;
import com.cms.iam.domain.RefreshToken;
import com.cms.iam.domain.Tenant;
import com.cms.iam.domain.User;
import com.cms.iam.dto.request.LoginRequest;
import com.cms.iam.repository.RefreshTokenRepository;
import com.cms.iam.repository.TenantRepository;
import com.cms.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;

    private static final int MAX_FAILED_ATTEMPTS_SOFT_LOCK = 5;
    private static final int MAX_FAILED_ATTEMPTS_HARD_LOCK = 10;
    private static final int SOFT_LOCK_MINUTES = 15;

    /**
     * Authenticates a user and returns a new access token + raw refresh token.
     *
     * <p>Brute force protection: per auth-flow.md § 3.5
     * - 5 failures → soft lock for 15 minutes
     * - 10 failures → hard lock (requires admin unlock)
     */
    @Transactional
    public LoginResult login(LoginRequest request, String ipAddress, String userAgent) {

        // 1. Resolve tenant
        Tenant tenant = tenantRepository.findByCode(request.getTenantCode())
                .orElseThrow(() -> new CmsException(ErrorCode.TENANT_NOT_FOUND));

        if (tenant.getStatus() != Tenant.TenantStatus.ACTIVE) {
            throw new CmsException(ErrorCode.TENANT_INACTIVE);
        }

        // 2. Find user (with roles + permissions pre-loaded — single query)
        User user = userRepository
                .findByEmailAndTenantCodeWithRoles(request.getEmail(), request.getTenantCode())
                .orElseThrow(() -> new CmsException(ErrorCode.INVALID_CREDENTIALS));

        // 3. Check account state before password check (prevents timing attack info leak)
        if (user.getStatus() == User.UserStatus.LOCKED) {
            // Check if soft lock has expired
            if (user.getLockedUntil() != null && OffsetDateTime.now().isBefore(user.getLockedUntil())) {
                throw new CmsException(ErrorCode.ACCOUNT_LOCKED);
            }
            // Soft lock expired — allow attempt but don't reset status yet
        }

        if (user.getStatus() == User.UserStatus.INACTIVE
                || user.getStatus() == User.UserStatus.PENDING) {
            throw new CmsException(ErrorCode.ACCOUNT_INACTIVE);
        }

        // 4. Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            handleFailedAttempt(user);
            throw new CmsException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 5. Reset failed attempts on successful auth
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        if (user.getStatus() == User.UserStatus.LOCKED) {
            user.setStatus(User.UserStatus.ACTIVE);  // unlock after successful login
        }
        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);

        // 6. Issue tokens
        String accessToken  = tokenService.issueAccessToken(user);
        String refreshToken = tokenService.issueRefreshToken(user, ipAddress, userAgent);

        log.info("User {} logged in from tenant {}", user.getEmail(), tenant.getCode());

        return new LoginResult(accessToken, refreshToken, tokenService.getAccessTokenTtlSeconds());
    }

    /**
     * Validates the incoming refresh token, rotates it, and issues a new access token.
     */
    @Transactional
    public LoginResult refresh(String rawRefreshToken, String ipAddress, String userAgent) {
        RefreshToken oldToken = tokenService.validateAndRevokeRefreshToken(rawRefreshToken);

        User user = userRepository
                .findByEmailAndTenantCodeWithRoles(
                        oldToken.getUser().getEmail(),
                        oldToken.getUser().getTenant().getCode())
                .orElseThrow(() -> new CmsException(ErrorCode.INVALID_CREDENTIALS));

        // Re-check user + tenant status at refresh time
        if (user.getTenant().getStatus() != Tenant.TenantStatus.ACTIVE) {
            throw new CmsException(ErrorCode.TENANT_INACTIVE);
        }
        if (user.getStatus() != User.UserStatus.ACTIVE) {
            throw new CmsException(ErrorCode.ACCOUNT_INACTIVE);
        }

        String accessToken     = tokenService.issueAccessToken(user);
        String newRefreshToken = tokenService.issueRefreshToken(user, ipAddress, userAgent);

        return new LoginResult(accessToken, newRefreshToken, tokenService.getAccessTokenTtlSeconds());
    }

    /**
     * Revokes the refresh token on logout.
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) return;
        // Best-effort revocation — ignore if token not found (already expired/revoked)
        try {
            tokenService.validateAndRevokeRefreshToken(rawRefreshToken);
        } catch (CmsException e) {
            log.debug("Logout: refresh token already invalid — {}", e.getMessage());
        }
    }

    /**
     * Force-revokes all active refresh tokens for a user (security incident response).
     */
    @Transactional
    public int revokeAllTokens(UUID userId) {
        return refreshTokenRepository.revokeAllForUser(userId, OffsetDateTime.now());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void handleFailedAttempt(User user) {
        int attempts = user.getFailedAttempts() + 1;
        user.setFailedAttempts(attempts);

        if (attempts >= MAX_FAILED_ATTEMPTS_HARD_LOCK) {
            user.setStatus(User.UserStatus.LOCKED);
            log.warn("User {} hard-locked after {} failed attempts", user.getEmail(), attempts);
        } else if (attempts >= MAX_FAILED_ATTEMPTS_SOFT_LOCK) {
            user.setStatus(User.UserStatus.LOCKED);                            // ← was missing
            user.setLockedUntil(OffsetDateTime.now().plusMinutes(SOFT_LOCK_MINUTES));
            log.warn("User {} soft-locked for {} minutes after {} failed attempts",
                    user.getEmail(), SOFT_LOCK_MINUTES, attempts);
        }

        userRepository.save(user);
    }

    // ── Result record ─────────────────────────────────────────────────────────

    public record LoginResult(String accessToken, String refreshToken, long expiresIn) {}
}
