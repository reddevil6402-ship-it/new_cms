package com.cms.content.domain;

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
@Table(name = "content_items", schema = "content")
public class ContentItem {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "content_type_id", nullable = false)
    private UUID contentTypeId;

    @Column(name = "content_type_code", nullable = false, length = 100)
    private String contentTypeCode;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 500)
    private String slug;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> body = Map.of();

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ContentStatus status = ContentStatus.DRAFT;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "category_code", length = 100)
    private String categoryCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ContentItem parent;

    @Column(name = "folder_path", columnDefinition = "text")
    private String folderPath;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "site_code", nullable = false, length = 100)
    private String siteCode;

    @Column(length = 100)
    private String persona;

    @Column(name = "publish_at")
    private OffsetDateTime publishAt;

    @Column(name = "expire_at")
    private OffsetDateTime expireAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "current_version", nullable = false)
    private int currentVersion = 1;

    @Column(name = "workflow_instance_id")
    private UUID workflowInstanceId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "media_ids", columnDefinition = "uuid[]")
    private List<UUID> mediaIds = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", columnDefinition = "text[]")
    private List<String> tags = new ArrayList<>();

    @Column(name = "external_link", columnDefinition = "text")
    private String externalLink;

    @Column(name = "order_index", nullable = false)
    private int orderIndex = 0;

    @Column(name = "is_featured", nullable = false)
    private boolean isFeatured = false;

    @Column(name = "view_count", nullable = false)
    private long viewCount = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> metadata = Map.of();

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Version
    @Column(nullable = false)
    private long version = 0;

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

    public enum ContentStatus {
        DRAFT, IN_REVIEW, APPROVED, PUBLISHED, SCHEDULED, ARCHIVED, TRASHED, REJECTED
    }
}
