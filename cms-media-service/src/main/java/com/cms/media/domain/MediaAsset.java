package com.cms.media.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.List;

@Entity
@Table(name = "media_assets", schema = "media")
@Getter
@Setter
public class MediaAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "stored_name", nullable = false)
    private String storedName;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    @Column(name = "storage_backend", nullable = false)
    private String storageBackend = "LOCAL";

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "checksum")
    private String checksum;

    @Column(name = "alt_text")
    private String altText;

    @Column(name = "caption")
    private String caption;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic = false;

    @Column(name = "folder_path", nullable = false)
    private String folderPath = "/";

    @Column(name = "tags")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> tags = new java.util.ArrayList<>();

    @Column(name = "metadata", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata = new java.util.HashMap<>();

    @Column(name = "cdn_url")
    private String cdnUrl;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "uploaded_at", nullable = false)
    private OffsetDateTime uploadedAt = OffsetDateTime.now();

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
