package com.cms.iam.controller;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Exposes the RSA public key as a JSON Web Key Set (JWKS).
 *
 * <p>All downstream services (gateway, content, schema, workflow) configure their
 * {@code JwtDecoder} to point to this endpoint. They download the public key and
 * cache it locally — they do NOT call this endpoint on every request.
 *
 * <p>Endpoint: GET /api/v1/auth/.well-known/jwks.json
 * This path is intentionally public — no authentication required.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class JwksController {

    private final RSAKey rsaKey;

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> jwks() {
        // Export only the PUBLIC key — never export the private key
        RSAKey publicKey = rsaKey.toPublicJWK();
        return new JWKSet(publicKey).toJSONObject();
    }
}
