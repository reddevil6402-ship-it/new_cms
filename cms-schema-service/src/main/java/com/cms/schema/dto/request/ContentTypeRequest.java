package com.cms.schema.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class ContentTypeRequest {

    @NotBlank(message = "Code is required")
    @Size(max = 100, message = "Code must not exceed 100 characters")
    @Pattern(regexp = "^[a-z0-9_]+$", message = "Code must contain only lowercase letters, numbers, and underscores")
    private String code;

    @NotBlank(message = "Display name is required")
    @Size(max = 255, message = "Display name must not exceed 255 characters")
    private String displayName;

    private String description;

    @Size(max = 100, message = "Icon name must not exceed 100 characters")
    private String icon;

    private boolean hierarchical = false;

    private boolean versionable = true;

    private boolean schedulable = true;

    private boolean hasWorkflow = false;

    private UUID workflowId;

    private String workflowCode;

    private String defaultPersona;

    private Map<String, Object> metadata = Map.of();

    @Valid
    private List<FieldDefinitionRequest> fieldDefinitions = new ArrayList<>();
}
