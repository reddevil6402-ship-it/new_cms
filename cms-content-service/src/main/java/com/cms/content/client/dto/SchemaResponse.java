package com.cms.content.client.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class SchemaResponse {

    private UUID id;
    private UUID tenantId;
    private String code;
    private String displayName;
    private boolean versionable;
    private boolean schedulable;
    private boolean hasWorkflow;
    private UUID workflowId;
    private String workflowCode;
    private List<FieldDefinitionDto> fieldDefinitions = new ArrayList<>();

    @Getter
    @Setter
    public static class FieldDefinitionDto {
        private UUID id;
        private String fieldKey;
        private String displayLabel;
        private String fieldType; // TEXT, NUMBER, DATE, DATETIME, BOOLEAN, RICHTEXT, JSON, RELATION, SELECT, MULTISELECT
        private boolean required;
        private boolean searchable;
        private boolean filterable;
        private boolean listable;
        private int displayOrder;
        private String defaultValue;
        private Map<String, Object> validationRules = Map.of();
    }
}
