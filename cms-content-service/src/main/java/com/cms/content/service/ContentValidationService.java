package com.cms.content.service;

import com.cms.common.exception.CmsException;
import com.cms.common.exception.ErrorCode;
import com.cms.content.client.dto.SchemaResponse;
import com.cms.content.client.dto.SchemaResponse.FieldDefinitionDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class ContentValidationService {

    /**
     * Validate content body map against schema definition.
     * Throws CmsException if validation fails.
     */
    public void validate(Map<String, Object> body, SchemaResponse schema) {
        log.debug("Validating content body against schema: {}", schema.getCode());

        if (schema.getFieldDefinitions() == null) {
            return;
        }

        for (FieldDefinitionDto field : schema.getFieldDefinitions()) {
            String key = field.getFieldKey();
            Object value = body.get(key);

            // 1. Required check
            if (field.isRequired()) {
                if (value == null || (value instanceof String str && str.trim().isEmpty())) {
                    throw new CmsException(ErrorCode.MISSING_REQUIRED_FIELD,
                            "Required field '" + key + "' (" + field.getDisplayLabel() + ") is missing or empty.");
                }
            }

            // 2. Type validation if value is present
            if (value != null) {
                validateType(key, value, field.getFieldType(), field.getDisplayLabel());
            }
        }
    }

    private void validateType(String key, Object value, String type, String label) {
        switch (type.toUpperCase()) {
            case "NUMBER":
                if (!(value instanceof Number)) {
                    try {
                        Double.parseDouble(value.toString());
                    } catch (NumberFormatException e) {
                        throw new CmsException(ErrorCode.VALIDATION_ERROR,
                                "Field '" + key + "' (" + label + ") must be a valid number, got: " + value);
                    }
                }
                break;

            case "BOOLEAN":
                if (!(value instanceof Boolean)) {
                    String strVal = value.toString().toLowerCase();
                    if (!"true".equals(strVal) && !"false".equals(strVal)) {
                        throw new CmsException(ErrorCode.VALIDATION_ERROR,
                                "Field '" + key + "' (" + label + ") must be a boolean, got: " + value);
                    }
                }
                break;

            case "DATE":
            case "DATETIME":
                // Basic ISO date check
                try {
                    DateTimeFormatter.ISO_DATE_TIME.parse(value.toString());
                } catch (Exception e) {
                    try {
                        DateTimeFormatter.ISO_DATE.parse(value.toString());
                    } catch (Exception ex) {
                        throw new CmsException(ErrorCode.VALIDATION_ERROR,
                                "Field '" + key + "' (" + label + ") must be a valid ISO date/datetime string, got: " + value);
                    }
                }
                break;

            case "RELATION":
                // Verify relation is UUID
                try {
                    UUID.fromString(value.toString());
                } catch (IllegalArgumentException e) {
                    throw new CmsException(ErrorCode.VALIDATION_ERROR,
                            "Field '" + key + "' (" + label + ") must be a valid UUID relation identifier, got: " + value);
                }
                break;

            default:
                // TEXT, RICHTEXT, JSON, SELECT, MULTISELECT need no strict Java type check
                break;
        }
    }
}
