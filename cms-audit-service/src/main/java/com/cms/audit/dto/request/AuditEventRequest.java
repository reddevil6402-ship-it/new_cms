package com.cms.audit.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.Map;
import java.util.UUID;

@Data
public class AuditEventRequest {
    @NotBlank
    private String eventType;
    
    private UUID actorId;
    private String actorEmail;
    private String entityType;
    private UUID entityId;
    private String sourceService;
    private Map<String, Object> oldValue;
    private Map<String, Object> newValue;
    private String ipAddress;
    private String userAgent;
    private UUID correlationId;
}
