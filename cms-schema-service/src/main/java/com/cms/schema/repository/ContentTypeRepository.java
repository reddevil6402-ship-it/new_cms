package com.cms.schema.repository;

import com.cms.schema.domain.ContentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContentTypeRepository extends JpaRepository<ContentType, UUID> {

    Optional<ContentType> findByTenantIdAndCode(UUID tenantId, String code);

    Optional<ContentType> findByTenantIdAndId(UUID tenantId, UUID id);

    List<ContentType> findAllByTenantId(UUID tenantId);
}
