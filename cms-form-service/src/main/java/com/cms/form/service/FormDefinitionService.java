package com.cms.form.service;

import com.cms.common.security.TenantContext;
import com.cms.form.domain.FormDefinition;
import com.cms.form.repository.FormDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FormDefinitionService {
    
    private final FormDefinitionRepository repository;

    public FormDefinition createForm(FormDefinition form) {
        form.setTenantId(UUID.fromString(TenantContext.getCurrentTenantId()));
        return repository.save(form);
    }

    public List<FormDefinition> getAllForms() {
        return repository.findAll();
    }

    /**
     * Used by authenticated endpoints — reads tenantId from TenantContext (populated by TenantContextFilter).
     */
    public Optional<FormDefinition> getFormByCode(String code) {
        return repository.findByTenantIdAndCode(UUID.fromString(TenantContext.getCurrentTenantId()), code);
    }

    /**
     * Used by the public submission endpoint — accepts tenantId directly from the X-Tenant-Id request header
     * because TenantContext is NOT populated for unauthenticated (public) requests.
     */
    public Optional<FormDefinition> getFormByCodeAndTenantId(UUID tenantId, String code) {
        return repository.findByTenantIdAndCode(tenantId, code);
    }
}
