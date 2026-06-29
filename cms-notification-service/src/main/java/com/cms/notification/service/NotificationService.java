package com.cms.notification.service;

import com.cms.common.exception.CmsException;
import com.cms.common.exception.ErrorCode;
import com.cms.common.security.TenantContext;
import com.cms.notification.domain.NotificationLog;
import com.cms.notification.domain.NotificationTemplate;
import com.cms.notification.dto.request.NotificationSendRequest;
import com.cms.notification.dto.request.NotificationTemplateRequest;
import com.cms.notification.repository.NotificationLogRepository;
import com.cms.notification.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationTemplateRepository templateRepository;
    private final NotificationLogRepository logRepository;

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
    public NotificationTemplate createTemplate(NotificationTemplateRequest request) {
        UUID tenantId = getTenantId();

        // Check if template already exists for channel
        templateRepository.findByTenantIdAndCodeAndChannel(tenantId, request.getCode(), request.getChannel())
                .ifPresent(t -> {
                    throw new CmsException(ErrorCode.DUPLICATE_RESOURCE, "Template already exists for this channel");
                });

        NotificationTemplate template = new NotificationTemplate();
        template.setTenantId(tenantId);
        template.setCode(request.getCode());
        template.setName(request.getName());
        template.setChannel(request.getChannel().toUpperCase());
        template.setSubject(request.getSubject());
        template.setBodyTemplate(request.getBodyTemplate());

        return templateRepository.save(template);
    }

    @Transactional(readOnly = true)
    public List<NotificationTemplate> listTemplates() {
        UUID tenantId = getTenantId();
        return templateRepository.findAllByTenantId(tenantId);
    }

    @Transactional
    public NotificationLog sendNotification(NotificationSendRequest request) {
        UUID tenantId = getTenantId();

        NotificationTemplate template = templateRepository.findByTenantIdAndCodeAndChannel(
                tenantId, request.getTemplateCode(), request.getChannel().toUpperCase())
                .orElseThrow(() -> new CmsException(ErrorCode.RESOURCE_NOT_FOUND, "Notification template not found"));

        if (!template.isActive()) {
            throw new CmsException(ErrorCode.VALIDATION_ERROR, "Notification template is inactive");
        }

        // Prepare log entry
        NotificationLog notificationLog = new NotificationLog();
        notificationLog.setTenantId(tenantId);
        notificationLog.setNotificationType(request.getChannel().toUpperCase());
        notificationLog.setRecipient(request.getRecipient());
        notificationLog.setTemplateCode(request.getTemplateCode());
        notificationLog.setPayload(request.getPayload() != null ? request.getPayload() : Map.of());

        try {
            // Format templates using Mustache-like interpolation
            String resolvedBody = resolveTemplate(template.getBodyTemplate(), request.getPayload());
            String resolvedSubject = template.getSubject() != null 
                    ? resolveTemplate(template.getSubject(), request.getPayload()) 
                    : null;

            log.info("Dispatching {} notification to [{}]. Subject: [{}], Body: [{}]", 
                    template.getChannel(), request.getRecipient(), resolvedSubject, resolvedBody);

            // MOCK DELIVERED
            notificationLog.setStatus("SENT");
            notificationLog.setSentAt(OffsetDateTime.now());

        } catch (Exception e) {
            log.error("Failed to compile/send notification template", e);
            notificationLog.setStatus("FAILED");
            notificationLog.setErrorMessage(e.getMessage());
        }

        return logRepository.save(notificationLog);
    }

    @Transactional(readOnly = true)
    public List<NotificationLog> getLogs() {
        UUID tenantId = getTenantId();
        return logRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    private String resolveTemplate(String template, Map<String, Object> payload) {
        if (template == null) return "";
        if (payload == null) return template;
        String result = template;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return result;
    }
}
