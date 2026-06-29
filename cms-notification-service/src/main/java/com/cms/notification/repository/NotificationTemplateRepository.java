package com.cms.notification.repository;

import com.cms.notification.domain.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {
    Optional<NotificationTemplate> findByTenantIdAndCodeAndChannel(UUID tenantId, String code, String channel);
    List<NotificationTemplate> findAllByTenantId(UUID tenantId);
}
