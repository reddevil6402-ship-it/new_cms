package com.cms.form.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "form_definitions", schema = "form")
@Getter
@Setter
public class FormDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schema", columnDefinition = "jsonb", nullable = false)
    private String schema; // Stores JSON Schema string

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ui_schema", columnDefinition = "jsonb", nullable = false)
    private String uiSchema; // Stores UI Schema string

    @Column(name = "submit_action", length = 100)
    private String submitAction;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(nullable = false)
    private int version = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
