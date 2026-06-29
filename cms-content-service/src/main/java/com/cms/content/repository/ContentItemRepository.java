package com.cms.content.repository;

import com.cms.content.domain.ContentItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContentItemRepository extends JpaRepository<ContentItem, UUID> {

    Optional<ContentItem> findByTenantIdAndIdAndDeletedAtIsNull(UUID tenantId, UUID id);

    List<ContentItem> findAllByTenantIdAndDeletedAtIsNull(UUID tenantId);

    List<ContentItem> findAllByTenantIdAndContentTypeCodeAndDeletedAtIsNull(UUID tenantId, String contentTypeCode);
}
