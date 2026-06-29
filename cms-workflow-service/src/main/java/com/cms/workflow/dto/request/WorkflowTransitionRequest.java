package com.cms.workflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class WorkflowTransitionRequest {

    @NotBlank(message = "Trigger is required")
    private String trigger;

    private String comment;

    private Map<String, Object> metadata = Map.of();
}
