package com.cms.content.service;

import com.cms.common.exception.CmsException;
import com.cms.common.exception.ErrorCode;
import com.cms.content.client.SchemaServiceClient;
import com.cms.content.client.WorkflowServiceClient;
import com.cms.content.client.dto.SchemaResponse;
import com.cms.content.client.dto.WorkflowInstanceDto;
import com.cms.content.domain.ContentItem;
import com.cms.content.domain.ContentItem.ContentStatus;
import com.cms.content.domain.ContentVersion;
import com.cms.content.dto.request.ContentRequest;
import com.cms.content.dto.response.ContentResponse;
import com.cms.content.dto.response.ContentVersionResponse;
import com.cms.content.repository.ContentItemRepository;
import com.cms.content.repository.ContentVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentService {

    private final ContentItemRepository contentItemRepository;
    private final ContentVersionRepository contentVersionRepository;
    private final SchemaServiceClient schemaServiceClient;
    private final WorkflowServiceClient workflowServiceClient;
    private final ContentValidationService validationService;

    @Transactional
    public ContentResponse createContentItem(UUID tenantId, ContentRequest request, UUID createdBy) {
        log.info("Creating content item: {} of type: {}", request.getTitle(), request.getContentTypeCode());

        // 1. Fetch schema definition from schema-service
        SchemaResponse schema = schemaServiceClient.getSchemaByCode(request.getContentTypeCode())
                .orElseThrow(() -> new CmsException(ErrorCode.CONTENT_TYPE_NOT_FOUND,
                        "Schema definition for content type '" + request.getContentTypeCode() + "' not found"));

        // 2. Validate payload body against schema
        validationService.validate(request.getBody(), schema);

        // 3. Populate ContentItem
        ContentItem item = new ContentItem();
        item.setTenantId(tenantId);
        item.setContentTypeId(schema.getId());
        item.setContentTypeCode(schema.getCode());
        item.setTitle(request.getTitle());
        item.setSlug(generateSlug(request.getSlug(), request.getTitle()));
        item.setBody(request.getBody());
        item.setStatus(ContentStatus.DRAFT); // Default state
        item.setCategoryId(request.getCategoryId());
        item.setCategoryCode(request.getCategoryCode());
        item.setSiteId(request.getSiteId());
        item.setSiteCode(request.getSiteCode());
        item.setPersona(request.getPersona());
        item.setMediaIds(request.getMediaIds());
        item.setTags(request.getTags());
        item.setExternalLink(request.getExternalLink());
        item.setOrderIndex(request.getOrderIndex());
        item.setFeatured(request.isFeatured());
        item.setMetadata(request.getMetadata());
        item.setCreatedBy(createdBy);
        item.setCurrentVersion(1);

        if (request.getParentId() != null) {
            ContentItem parent = contentItemRepository.findById(request.getParentId())
                    .orElseThrow(() -> new CmsException(ErrorCode.RESOURCE_NOT_FOUND, "Parent content item not found"));
            item.setParent(parent);
        }

        // Save initial item
        ContentItem savedItem = contentItemRepository.save(item);

        // 4. Spawn workflow instance automatically
        if (request.getWorkflowCode() != null && !request.getWorkflowCode().isBlank()) {
            Optional<WorkflowInstanceDto> workflow = workflowServiceClient
                    .startWorkflowInstance(request.getWorkflowCode(), savedItem.getId());
            if (workflow.isPresent()) {
                savedItem.setWorkflowInstanceId(workflow.get().getId());
                savedItem = contentItemRepository.save(savedItem);
                log.info("Linked workflow instance {} to content item {}", workflow.get().getId(), savedItem.getId());
            }
        }

        // 5. Create immutable version history entry (v1)
        saveVersionSnapshot(savedItem, "Initial creation", createdBy);

        return ContentResponse.fromEntity(savedItem);
    }

    @Transactional(readOnly = true)
    public ContentResponse getContentItemById(UUID tenantId, UUID id) {
        ContentItem item = contentItemRepository.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
                .orElseThrow(() -> new CmsException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Content item with id '" + id + "' not found or access is denied"));
        return ContentResponse.fromEntity(item);
    }

    @Transactional(readOnly = true)
    public List<ContentResponse> getContentItemsByTypeCode(UUID tenantId, String typeCode) {
        return contentItemRepository.findAllByTenantIdAndContentTypeCodeAndDeletedAtIsNull(tenantId, typeCode).stream()
                .map(ContentResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ContentResponse> getAllContentItems(UUID tenantId) {
        return contentItemRepository.findAllByTenantIdAndDeletedAtIsNull(tenantId).stream()
                .map(ContentResponse::fromEntity)
                .toList();
    }

    @Transactional
    public ContentResponse updateContentItem(UUID tenantId, UUID id, ContentRequest request, UUID updatedBy) {
        log.info("Updating content item with id: {}", id);

        ContentItem existing = contentItemRepository.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
                .orElseThrow(() -> new CmsException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Content item with id '" + id + "' not found or access is denied"));

        // Fetch schema
        SchemaResponse schema = schemaServiceClient.getSchemaByCode(existing.getContentTypeCode())
                .orElseThrow(() -> new CmsException(ErrorCode.CONTENT_TYPE_NOT_FOUND,
                        "Schema definition for content type '" + existing.getContentTypeCode() + "' not found"));

        // Validate payload
        validationService.validate(request.getBody(), schema);

        // Update fields
        existing.setTitle(request.getTitle());
        existing.setSlug(generateSlug(request.getSlug(), request.getTitle()));
        existing.setBody(request.getBody());
        existing.setCategoryId(request.getCategoryId());
        existing.setCategoryCode(request.getCategoryCode());
        existing.setSiteId(request.getSiteId());
        existing.setSiteCode(request.getSiteCode());
        existing.setPersona(request.getPersona());
        existing.setMediaIds(request.getMediaIds());
        existing.setTags(request.getTags());
        existing.setExternalLink(request.getExternalLink());
        existing.setOrderIndex(request.getOrderIndex());
        existing.setFeatured(request.isFeatured());
        existing.setMetadata(request.getMetadata());
        existing.setUpdatedBy(updatedBy);

        // Increment version number
        int nextVersion = existing.getCurrentVersion() + 1;
        existing.setCurrentVersion(nextVersion);

        ContentItem saved = contentItemRepository.save(existing);

        // Create version snapshot
        String summary = request.getChangeSummary() != null ? request.getChangeSummary() : "Revision updated";
        saveVersionSnapshot(saved, summary, updatedBy);

        return ContentResponse.fromEntity(saved);
    }

    @Transactional
    public void deleteContentItem(UUID tenantId, UUID id, UUID deletedBy) {
        log.info("Soft-deleting content item: {}", id);
        ContentItem existing = contentItemRepository.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
                .orElseThrow(() -> new CmsException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Content item with id '" + id + "' not found or access is denied"));

        existing.setDeletedAt(OffsetDateTime.now());
        existing.setDeletedBy(deletedBy);
        contentItemRepository.save(existing);
    }

    @Transactional(readOnly = true)
    public List<ContentVersionResponse> getContentVersions(UUID tenantId, UUID itemId) {
        // Confirm ownership
        contentItemRepository.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, itemId)
                .orElseThrow(() -> new CmsException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Content item with id '" + itemId + "' not found or access is denied"));

        return contentVersionRepository.findAllByContentItemIdOrderByVersionNumberDesc(itemId).stream()
                .map(ContentVersionResponse::fromEntity)
                .toList();
    }

    private void saveVersionSnapshot(ContentItem item, String changeSummary, UUID creatorId) {
        ContentVersion version = new ContentVersion();
        version.setContentItem(item);
        version.setVersionNumber(item.getCurrentVersion());
        version.setTitle(item.getTitle());
        version.setBody(item.getBody());
        version.setStatus(item.getStatus().name());
        version.setChangeSummary(changeSummary);
        version.setCreatedBy(creatorId);
        contentVersionRepository.save(version);
        log.debug("Saved content version snapshot v{} for item {}", item.getCurrentVersion(), item.getId());
    }

    private String generateSlug(String userSlug, String title) {
        if (userSlug != null && !userSlug.isBlank()) {
            return userSlug.toLowerCase().trim().replaceAll("[^a-z0-9_\\-]+", "-");
        }
        return title.toLowerCase().trim()
                .replaceAll("[^a-z0-9_\\-]+", "-")
                .replaceAll("-+", "-");
    }
}
