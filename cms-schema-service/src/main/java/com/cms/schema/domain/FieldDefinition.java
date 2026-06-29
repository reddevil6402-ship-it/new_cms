package com.cms.schema.domain;

import com.cms.common.util.UuidUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "field_definitions", schema = "schema")
public class FieldDefinition {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_type_id", nullable = false)
    @JsonIgnore
    private ContentType contentType;

    @Column(name = "field_key", nullable = false, length = 100)
    private String fieldKey;

    @Column(name = "display_label", nullable = false, length = 255)
    private String displayLabel;

    @Column(name = "field_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private FieldType fieldType;

    @Column(name = "is_required", nullable = false)
    private boolean isRequired = false;

    @Column(name = "is_searchable", nullable = false)
    private boolean isSearchable = false;

    @Column(name = "is_filterable", nullable = false)
    private boolean isFilterable = false;

    @Column(name = "is_listable", nullable = false)
    private boolean isListable = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "default_value", columnDefinition = "text")
    private String defaultValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_rules", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> validationRules = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ui_config", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> uiConfig = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "relation_config", columnDefinition = "jsonb")
    private Map<String, Object> relationConfig;

    @Column(name = "group_name", length = 100)
    private String groupName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void prePersist() {
        if (id == null) {
            id = UuidUtils.generateV7();
        }
        createdAt = OffsetDateTime.now();
    }

    public enum FieldType {
        TEXT, NUMBER, DATE, DATETIME, BOOLEAN, FILE, RICHTEXT, JSON, RELATION, SELECT, MULTISELECT
    }
}
