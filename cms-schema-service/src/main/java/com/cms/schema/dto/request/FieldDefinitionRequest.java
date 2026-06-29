package com.cms.schema.dto.request;

import com.cms.schema.domain.FieldDefinition.FieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class FieldDefinitionRequest {

    @NotBlank(message = "Field key is required")
    @Size(max = 100, message = "Field key must not exceed 100 characters")
    @Pattern(regexp = "^[a-z0-9_]+$", message = "Field key must contain only lowercase letters, numbers, and underscores")
    private String fieldKey;

    @NotBlank(message = "Display label is required")
    @Size(max = 255, message = "Display label must not exceed 255 characters")
    private String displayLabel;

    @NotNull(message = "Field type is required")
    private FieldType fieldType;

    private boolean required = false;

    private boolean searchable = false;

    private boolean filterable = false;

    private boolean listable = true;

    private int displayOrder = 0;

    private String defaultValue;

    private Map<String, Object> validationRules = Map.of();

    private Map<String, Object> uiConfig = Map.of();

    private Map<String, Object> relationConfig;

    private String groupName;
}
