package com.cms.iam.controller;

import com.cms.common.dto.ApiResponse;
import com.cms.iam.dto.request.LoginRequest;
import com.cms.iam.dto.response.LoginResponse;
import com.cms.iam.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final int REFRESH_COOKIE_MAX_AGE_SECONDS = 7 * 24 * 3600; // 7 days

    private final AuthService authService;

    /**
     * POST /api/v1/auth/login
     *
     * <p>Returns access token in the JSON body.
     * Refresh token is set as an HttpOnly, Secure, SameSite=Strict cookie.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        AuthService.LoginResult result = authService.login(
                request,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent"));

        setRefreshTokenCookie(httpResponse, result.refreshToken());

        LoginResponse body = LoginResponse.builder()
                .accessToken(result.accessToken())
                .tokenType("Bearer")
                .expiresIn(result.expiresIn())
                .build();

        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    /**
     * POST /api/v1/auth/refresh
     *
     * <p>Reads refresh token from HttpOnly cookie.
     * Returns new access token in body. Sets new refresh token cookie.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(new com.cms.common.dto.ErrorResponse(
                            "AUTH_008", "Refresh token cookie is missing", null)));
        }

        AuthService.LoginResult result = authService.refresh(
                refreshToken,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent"));

        setRefreshTokenCookie(httpResponse, result.refreshToken());

        LoginResponse body = LoginResponse.builder()
                .accessToken(result.accessToken())
                .tokenType("Bearer")
                .expiresIn(result.expiresIn())
                .build();

        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    /**
     * POST /api/v1/auth/logout
     *
     * <p>Revokes the refresh token and clears the cookie.
     * Best-effort — always returns 200 to prevent info leak.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse httpResponse) {

        authService.logout(refreshToken);
        clearRefreshTokenCookie(httpResponse);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ── Cookie helpers ────────────────────────────────────────────────────────

    /**
     * Sets the refresh token as an HttpOnly cookie.
     * Uses addHeader directly (not addCookie) to get full control over SameSite
     * and to avoid sending a duplicate Set-Cookie header.
     *
     * Secure=false for local HTTP dev — MUST be true in production.
     */
    private void setRefreshTokenCookie(HttpServletResponse response, String token) {
        String cookie = String.format(
                "%s=%s; Path=/api/v1/auth; HttpOnly; SameSite=Strict; Max-Age=%d",
                REFRESH_COOKIE_NAME, token, REFRESH_COOKIE_MAX_AGE_SECONDS);
        response.setHeader("Set-Cookie", cookie);
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        String cookie = String.format(
                "%s=; Path=/api/v1/auth; HttpOnly; SameSite=Strict; Max-Age=0",
                REFRESH_COOKIE_NAME);
        response.setHeader("Set-Cookie", cookie);
    }
}
