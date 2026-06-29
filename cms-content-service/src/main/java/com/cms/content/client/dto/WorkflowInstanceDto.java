package com.cms.content.client.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class WorkflowInstanceDto {

    private UUID id;
    private UUID workflowDefId;
    private String workflowDefCode;
    private int workflowDefVersion;
    private String entityType;
    private UUID entityId;
    private String currentState;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
}
