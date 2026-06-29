package com.cms.iam.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "permissions", schema = "iam")
public class Permission {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 100)
    private String resource;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(length = 50)
    private String scope;

    @Column
    private String description;

    @PrePersist
    protected void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }

    /**
     * Returns the permission string as embedded in the JWT permissions[] claim.
     * Format: "resource:ACTION:SCOPE" e.g. "content:CREATE:OWN"
     */
    public String toPermissionString() {
        return scope != null
                ? resource + ":" + action + ":" + scope
                : resource + ":" + action;
    }
}
