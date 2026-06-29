package com.cms.schema.dto.response;

import com.cms.schema.domain.FieldDefinition;
import com.cms.schema.domain.FieldDefinition.FieldType;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class FieldDefinitionResponse {

    private UUID id;
    private String fieldKey;
    private String displayLabel;
    private FieldType fieldType;
    private boolean required;
    private boolean searchable;
    private boolean filterable;
    private boolean listable;
    private int displayOrder;
    private String defaultValue;
    private Map<String, Object> validationRules;
    private Map<String, Object> uiConfig;
    private Map<String, Object> relationConfig;
    private String groupName;
    private OffsetDateTime createdAt;

    public static FieldDefinitionResponse fromEntity(FieldDefinition entity) {
        if (entity == null) return null;
        FieldDefinitionResponse resp = new FieldDefinitionResponse();
        resp.setId(entity.getId());
        resp.setFieldKey(entity.getFieldKey());
        resp.setDisplayLabel(entity.getDisplayLabel());
        resp.setFieldType(entity.getFieldType());
        resp.setRequired(entity.isRequired());
        resp.setSearchable(entity.isSearchable());
        resp.setFilterable(entity.isFilterable());
        resp.setListable(entity.isListable());
        resp.setDisplayOrder(entity.getDisplayOrder());
        resp.setDefaultValue(entity.getDefaultValue());
        resp.setValidationRules(entity.getValidationRules());
        resp.setUiConfig(entity.getUiConfig());
        resp.setRelationConfig(entity.getRelationConfig());
        resp.setGroupName(entity.getGroupName());
        resp.setCreatedAt(entity.getCreatedAt());
        return resp;
    }
}
