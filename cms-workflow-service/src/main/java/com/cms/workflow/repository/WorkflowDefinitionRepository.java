package com.cms.workflow.repository;

import com.cms.workflow.domain.WorkflowDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, UUID> {

    Optional<WorkflowDefinition> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<WorkflowDefinition> findByTenantIdAndCodeAndVersion(UUID tenantId, String code, int version);

    List<WorkflowDefinition> findAllByTenantId(UUID tenantId);

    List<WorkflowDefinition> findAllByTenantIdAndIsActiveTrue(UUID tenantId);

    // Get the latest version for a workflow code
    Optional<WorkflowDefinition> findFirstByTenantIdAndCodeOrderByVersionDesc(UUID tenantId, String code);
}
