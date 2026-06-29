package com.cms.audit.repository;

import com.cms.audit.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    List<AuditEvent> findAllByTenantIdOrderByOccurredAtDesc(UUID tenantId);
    List<AuditEvent> findAllByTenantIdAndEntityTypeAndEntityIdOrderByOccurredAtDesc(UUID tenantId, String entityType, UUID entityId);
}
