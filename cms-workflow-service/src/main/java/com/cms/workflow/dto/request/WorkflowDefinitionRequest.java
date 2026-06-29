package com.cms.workflow.dto.request;

import com.cms.workflow.domain.WorkflowDefinition.DefinitionDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WorkflowDefinitionRequest {

    @NotBlank(message = "Code is required")
    @Size(max = 100, message = "Code must not exceed 100 characters")
    @Pattern(regexp = "^[a-z0-9_]+$", message = "Code must contain only lowercase letters, numbers, and underscores")
    private String code;

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    private String description;

    @NotNull(message = "Definition details are required")
    @Valid
    private DefinitionDetails definition;
}
