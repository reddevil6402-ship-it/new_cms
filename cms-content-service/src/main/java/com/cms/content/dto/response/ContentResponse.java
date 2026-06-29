package com.cms.content.dto.response;

import com.cms.content.domain.ContentItem;
import com.cms.content.domain.ContentItem.ContentStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class ContentResponse {

    private UUID id;
    private UUID tenantId;
    private UUID contentTypeId;
    private String contentTypeCode;
    private String title;
    private String slug;
    private Map<String, Object> body;
    private ContentStatus status;
    private UUID categoryId;
    private String categoryCode;
    private UUID parentId;
    private String folderPath;
    private UUID siteId;
    private String siteCode;
    private String persona;
    private OffsetDateTime publishAt;
    private OffsetDateTime expireAt;
    private OffsetDateTime publishedAt;
    private int currentVersion;
    private UUID workflowInstanceId;
    private List<UUID> mediaIds;
    private List<String> tags;
    private String externalLink;
    private int orderIndex;
    private boolean featured;
    private long viewCount;
    private Map<String, Object> metadata;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;

    public static ContentResponse fromEntity(ContentItem entity) {
        if (entity == null) return null;
        ContentResponse resp = new ContentResponse();
        resp.setId(entity.getId());
        resp.setTenantId(entity.getTenantId());
        resp.setContentTypeId(entity.getContentTypeId());
        resp.setContentTypeCode(entity.getContentTypeCode());
        resp.setTitle(entity.getTitle());
        resp.setSlug(entity.getSlug());
        resp.setBody(entity.getBody());
        resp.setStatus(entity.getStatus());
        resp.setCategoryId(entity.getCategoryId());
        resp.setCategoryCode(entity.getCategoryCode());
        resp.setParentId(entity.getParent() != null ? entity.getParent().getId() : null);
        resp.setFolderPath(entity.getFolderPath());
        resp.setSiteId(entity.getSiteId());
        resp.setSiteCode(entity.getSiteCode());
        resp.setPersona(entity.getPersona());
        resp.setPublishAt(entity.getPublishAt());
        resp.setExpireAt(entity.getExpireAt());
        resp.setPublishedAt(entity.getPublishedAt());
        resp.setCurrentVersion(entity.getCurrentVersion());
        resp.setWorkflowInstanceId(entity.getWorkflowInstanceId());
        resp.setMediaIds(entity.getMediaIds());
        resp.setTags(entity.getTags());
        resp.setExternalLink(entity.getExternalLink());
        resp.setOrderIndex(entity.getOrderIndex());
        resp.setFeatured(entity.isFeatured());
        resp.setViewCount(entity.getViewCount());
        resp.setMetadata(entity.getMetadata());
        resp.setCreatedAt(entity.getCreatedAt());
        resp.setUpdatedAt(entity.getUpdatedAt());
        resp.setCreatedBy(entity.getCreatedBy());
        resp.setUpdatedBy(entity.getUpdatedBy());
        return resp;
    }
}
