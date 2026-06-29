package com.cms.iam.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

/**
 * RSA key pair configuration for JWT signing and verification.
 *
 * <p><strong>Local development:</strong> Generates a new RSA-2048 key pair on every
 * startup. This means JWTs are invalidated on restart — expected and acceptable locally.
 *
 * <p><strong>Production:</strong> Mount a persistent key pair via environment variables
 * or a secret manager. Set {@code JWT_PRIVATE_KEY_PATH} and {@code JWT_PUBLIC_KEY_PATH}
 * to point to PEM files. Key loading from files will be implemented as a Phase 2 enhancement
 * when deployment target is decided.
 *
 * <p>The key ID ({@code kid}) is set to {@code cms-key-v1} — used by the JWKS endpoint
 * and by downstream services to resolve the correct public key for verification.
 */
@Configuration
public class JwtConfig {

    @Value("${cms.jwt.key-id:cms-key-v1}")
    private String keyId;

    /**
     * Generates RSA-2048 key pair for local dev.
     * Replace with persistent key loading for production.
     */
    @Bean
    public KeyPair jwtKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate RSA key pair for JWT signing", e);
        }
    }

    @Bean
    public RSAKey rsaKey(KeyPair keyPair) {
        return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(keyId)
                .build();
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(RSAKey rsaKey) {
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    /**
     * JwtEncoder — used by TokenService to SIGN and ISSUE JWTs.
     * Only iam-service has this bean. No other service issues tokens.
     */
    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * JwtDecoder — used by Spring Security resource server to VALIDATE incoming JWTs.
     * Downstream services get their JwtDecoder from the JWKS endpoint, not from here.
     */
    @Bean
    public JwtDecoder jwtDecoder(RSAKey rsaKey) {
        try {
            return NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build JwtDecoder from RSA public key", e);
        }
    }
}
