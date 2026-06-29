package com.cms.schema.dto.response;

import com.cms.schema.domain.ContentType;
import com.cms.schema.domain.ContentType.ContentTypeStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class ContentTypeResponse {

    private UUID id;
    private UUID tenantId;
    private String code;
    private String displayName;
    private String description;
    private String icon;
    private boolean hierarchical;
    private boolean versionable;
    private boolean schedulable;
    private boolean hasWorkflow;
    private UUID workflowId;
    private String workflowCode;
    private String defaultPersona;
    private Map<String, Object> metadata;
    private ContentTypeStatus status;
    private int version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private UUID createdBy;
    private List<FieldDefinitionResponse> fieldDefinitions;

    public static ContentTypeResponse fromEntity(ContentType entity) {
        if (entity == null) return null;
        ContentTypeResponse resp = new ContentTypeResponse();
        resp.setId(entity.getId());
        resp.setTenantId(entity.getTenantId());
        resp.setCode(entity.getCode());
        resp.setDisplayName(entity.getDisplayName());
        resp.setDescription(entity.getDescription());
        resp.setIcon(entity.getIcon());
        resp.setHierarchical(entity.isHierarchical());
        resp.setVersionable(entity.isVersionable());
        resp.setSchedulable(entity.isSchedulable());
        resp.setHasWorkflow(entity.isHasWorkflow());
        resp.setWorkflowId(entity.getWorkflowId());
        resp.setWorkflowCode(entity.getWorkflowCode());
        resp.setDefaultPersona(entity.getDefaultPersona());
        resp.setMetadata(entity.getMetadata());
        resp.setStatus(entity.getStatus());
        resp.setVersion(entity.getVersion());
        resp.setCreatedAt(entity.getCreatedAt());
        resp.setUpdatedAt(entity.getUpdatedAt());
        resp.setCreatedBy(entity.getCreatedBy());

        if (entity.getFieldDefinitions() != null) {
            resp.setFieldDefinitions(entity.getFieldDefinitions().stream()
                    .map(FieldDefinitionResponse::fromEntity)
                    .toList());
        }

        return resp;
    }
}
