package com.cms.workflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class WorkflowInstanceRequest {

    @NotBlank(message = "Workflow code is required")
    private String workflowCode;

    @NotBlank(message = "Entity type is required")
    private String entityType;

    @NotNull(message = "Entity ID is required")
    private UUID entityId;

    private Map<String, Object> metadata = Map.of();
}
