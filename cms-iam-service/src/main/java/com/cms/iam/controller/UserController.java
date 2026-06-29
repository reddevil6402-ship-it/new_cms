package com.cms.iam.controller;

import com.cms.common.dto.ApiResponse;
import com.cms.iam.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * User management controller for cms-iam-service.
 *
 * Phase 1 scope: force-revoke tokens (security incident response).
 * Full CRUD (create/update/deactivate user, role assignment) is a Phase 2 item —
 * for Phase 1, users are seeded via Flyway migrations.
 */
@RestController
@RequestMapping("/api/v1/iam/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    /**
     * POST /api/v1/iam/users/{userId}/revoke-all-tokens
     *
     * <p>Force-revokes ALL active refresh tokens for a user.
     * Use for security incidents: compromised account, suspicious activity, admin-initiated lockout.
     *
     * <p>The user will be logged out from all devices. Their next refresh attempt will fail.
     * Their existing access tokens remain valid until natural expiry (max 15 minutes) —
     * access token blacklisting is a Phase 2 feature (Redis-based).
     *
     * <p>Requires: {@code user:DELETE:ALL} permission (SUPER_ADMIN and TENANT_ADMIN only).
     */
    @PostMapping("/{userId}/revoke-all-tokens")
    @PreAuthorize("hasAuthority('user:DELETE:ALL')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> revokeAllTokens(
            @PathVariable UUID userId) {

        int revoked = authService.revokeAllTokens(userId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "userId", userId.toString(),
                "revokedTokenCount", revoked,
                "message", revoked > 0
                        ? revoked + " active refresh token(s) revoked. User is now logged out from all devices."
                        : "No active refresh tokens found. User was already logged out from all devices."
        )));
    }
}
