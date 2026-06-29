package com.cms.form.repository;

import com.cms.form.domain.FormDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FormDefinitionRepository extends JpaRepository<FormDefinition, UUID> {
    Optional<FormDefinition> findByTenantIdAndCode(UUID tenantId, String code);
}
