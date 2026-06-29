package com.cms.workflow.repository;

import com.cms.workflow.domain.WorkflowInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, UUID> {

    Optional<WorkflowInstance> findByEntityTypeAndEntityId(String entityType, UUID entityId);

    @Query("SELECT wi FROM WorkflowInstance wi JOIN wi.workflowDefinition wd WHERE wd.tenantId = :tenantId AND wi.id = :id")
    Optional<WorkflowInstance> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
