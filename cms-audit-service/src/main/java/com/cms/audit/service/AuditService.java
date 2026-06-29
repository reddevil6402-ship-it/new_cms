package com.cms.audit.service;

import com.cms.common.exception.CmsException;
import com.cms.common.exception.ErrorCode;
import com.cms.common.security.TenantContext;
import com.cms.audit.domain.AuditEvent;
import com.cms.audit.dto.request.AuditEventRequest;
import com.cms.audit.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    private UUID getTenantId() {
        String tenantIdStr = TenantContext.getCurrentTenantId();
        if (tenantIdStr == null || tenantIdStr.isBlank()) {
            throw new CmsException(ErrorCode.ACCESS_DENIED, "Tenant context is missing");
        }
        try {
            return UUID.fromString(tenantIdStr);
        } catch (IllegalArgumentException e) {
            throw new CmsException(ErrorCode.VALIDATION_ERROR, "Invalid Tenant ID format: " + tenantIdStr);
        }
    }

    @Transactional
    public AuditEvent logEvent(AuditEventRequest request) {
        UUID tenantId = getTenantId();

        AuditEvent event = new AuditEvent();
        event.setTenantId(tenantId);
        event.setEventType(request.getEventType());
        event.setActorId(request.getActorId());
        event.setActorEmail(request.getActorEmail());
        event.setEntityType(request.getEntityType());
        event.setEntityId(request.getEntityId());
        event.setSourceService(request.getSourceService());
        event.setOldValue(request.getOldValue());
        event.setNewValue(request.getNewValue());
        event.setIpAddress(request.getIpAddress());
        event.setUserAgent(request.getUserAgent());
        event.setCorrelationId(request.getCorrelationId());
        event.setOccurredAt(OffsetDateTime.now());

        log.info("Recording audit event: {} for entity: {}", request.getEventType(), request.getEntityId());
        return auditEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> getLogs(String entityType, UUID entityId) {
        UUID tenantId = getTenantId();
        if (entityType != null && entityId != null) {
            return auditEventRepository.findAllByTenantIdAndEntityTypeAndEntityIdOrderByOccurredAtDesc(tenantId, entityType, entityId);
        }
        return auditEventRepository.findAllByTenantIdOrderByOccurredAtDesc(tenantId);
    }
}
