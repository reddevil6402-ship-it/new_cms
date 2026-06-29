package com.cms.media.repository;

import com.cms.media.domain.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {
    Optional<MediaAsset> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);
    List<MediaAsset> findAllByTenantIdAndDeletedAtIsNull(UUID tenantId);
}
