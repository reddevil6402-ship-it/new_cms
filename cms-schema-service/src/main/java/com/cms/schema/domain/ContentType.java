package com.cms.schema.domain;

import com.cms.common.util.UuidUtils;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "content_types", schema = "schema")
public class ContentType {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 100)
    private String icon;

    @Column(name = "is_hierarchical", nullable = false)
    private boolean isHierarchical = false;

    @Column(name = "is_versionable", nullable = false)
    private boolean isVersionable = true;

    @Column(name = "is_schedulable", nullable = false)
    private boolean isSchedulable = true;

    @Column(name = "has_workflow", nullable = false)
    private boolean hasWorkflow = false;

    @Column(name = "workflow_id", columnDefinition = "uuid")
    private UUID workflowId;

    @Column(name = "workflow_code", length = 100)
    private String workflowCode;

    @Column(name = "default_persona", length = 100)
    private String defaultPersona;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> metadata = Map.of();

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ContentTypeStatus status = ContentTypeStatus.ACTIVE;

    @Version
    @Column(nullable = false)
    private int version = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", columnDefinition = "uuid")
    private UUID createdBy;

    @OneToMany(mappedBy = "contentType", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<FieldDefinition> fieldDefinitions = new ArrayList<>();

    @PrePersist
    protected void prePersist() {
        if (id == null) {
            id = UuidUtils.generateV7();
        }
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public void addFieldDefinition(FieldDefinition field) {
        fieldDefinitions.add(field);
        field.setContentType(this);
    }

    public void removeFieldDefinition(FieldDefinition field) {
        fieldDefinitions.remove(field);
        field.setContentType(null);
    }

    public enum ContentTypeStatus {
        ACTIVE, DRAFT, DEPRECATED
    }
}
