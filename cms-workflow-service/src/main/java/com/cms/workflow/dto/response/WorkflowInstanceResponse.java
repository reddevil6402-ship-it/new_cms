package com.cms.workflow.dto.response;

import com.cms.workflow.domain.WorkflowInstance;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class WorkflowInstanceResponse {

    private UUID id;
    private UUID workflowDefId;
    private String workflowDefCode;
    private int workflowDefVersion;
    private String entityType;
    private UUID entityId;
    private String currentState;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private Map<String, Object> metadata;
    private List<WorkflowHistoryResponse> history;

    public static WorkflowInstanceResponse fromEntity(WorkflowInstance entity) {
        if (entity == null) return null;
        WorkflowInstanceResponse resp = new WorkflowInstanceResponse();
        resp.setId(entity.getId());
        resp.setWorkflowDefId(entity.getWorkflowDefinition().getId());
        resp.setWorkflowDefCode(entity.getWorkflowDefinition().getCode());
        resp.setWorkflowDefVersion(entity.getWorkflowDefVersion());
        resp.setEntityType(entity.getEntityType());
        resp.setEntityId(entity.getEntityId());
        resp.setCurrentState(entity.getCurrentState());
        resp.setStartedAt(entity.getStartedAt());
        resp.setCompletedAt(entity.getCompletedAt());
        resp.setMetadata(entity.getMetadata());

        if (entity.getHistory() != null) {
            resp.setHistory(entity.getHistory().stream()
                    .map(WorkflowHistoryResponse::fromEntity)
                    .toList());
        }

        return resp;
    }
}
