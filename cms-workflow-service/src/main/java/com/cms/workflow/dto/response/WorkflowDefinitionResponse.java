package com.cms.workflow.dto.response;

import com.cms.workflow.domain.WorkflowDefinition;
import com.cms.workflow.domain.WorkflowDefinition.DefinitionDetails;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class WorkflowDefinitionResponse {

    private UUID id;
    private UUID tenantId;
    private String code;
    private String name;
    private String description;
    private DefinitionDetails definition;
    private int version;
    private boolean active;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static WorkflowDefinitionResponse fromEntity(WorkflowDefinition entity) {
        if (entity == null) return null;
        WorkflowDefinitionResponse resp = new WorkflowDefinitionResponse();
        resp.setId(entity.getId());
        resp.setTenantId(entity.getTenantId());
        resp.setCode(entity.getCode());
        resp.setName(entity.getName());
        resp.setDescription(entity.getDescription());
        resp.setDefinition(entity.getDefinition());
        resp.setVersion(entity.getVersion());
        resp.setActive(entity.isActive());
        resp.setCreatedAt(entity.getCreatedAt());
        resp.setUpdatedAt(entity.getUpdatedAt());
        return resp;
    }
}
