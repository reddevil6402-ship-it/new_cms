package com.cms.iam.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "refresh_tokens", schema = "iam")
public class RefreshToken {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 255)
    private String tokenHash;    // SHA-256 of the raw token — raw token never stored

    @Column(nullable = false)
    private OffsetDateTime expiresAt;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime issuedAt;

    @Column
    private OffsetDateTime revokedAt;

    // @ColumnTransformer tells Hibernate to cast the Java String to PostgreSQL inet on write.
    // PostgreSQL's inet type won't accept a plain VARCHAR bind — the explicit ::inet cast is required.
    @ColumnTransformer(write = "?::inet")
    @Column(name = "ip_address", columnDefinition = "inet")
    private String ipAddress;

    @Column
    private String userAgent;

    @PrePersist
    protected void prePersist() {
        if (id == null) id = UUID.randomUUID();
        issuedAt = OffsetDateTime.now();
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired() {
        return OffsetDateTime.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !isRevoked() && !isExpired();
    }
}
