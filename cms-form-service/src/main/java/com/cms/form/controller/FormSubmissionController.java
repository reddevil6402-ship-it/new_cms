package com.cms.form.controller;

import com.cms.form.domain.FormDefinition;
import com.cms.form.domain.FormSubmission;
import com.cms.form.dto.request.FormSubmitRequest;
import com.cms.form.service.FormDefinitionService;
import com.cms.form.service.FormSubmissionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/forms")
@RequiredArgsConstructor
public class FormSubmissionController {

    private final FormDefinitionService definitionService;
    private final FormSubmissionService submissionService;

    /**
     * Public endpoint — no JWT required.
     * Tenant is identified via the X-Tenant-Id header (sent by the browser / gateway).
     * TenantContext is NOT available here since the request is unauthenticated.
     */
    @PostMapping("/{code}/submit")
    public ResponseEntity<?> submitForm(
            @PathVariable String code,
            @RequestBody FormSubmitRequest request,
            HttpServletRequest httpRequest) {

        String tenantHeader = httpRequest.getHeader("X-Tenant-Id");
        if (tenantHeader == null || tenantHeader.isBlank()) {
            return ResponseEntity.badRequest().body("Missing X-Tenant-Id header.");
        }

        UUID tenantId;
        try {
            tenantId = UUID.fromString(tenantHeader);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid X-Tenant-Id header — must be a valid UUID.");
        }

        FormDefinition definition = definitionService.getFormByCodeAndTenantId(tenantId, code).orElse(null);
        if (definition == null || !definition.isActive()) {
            return ResponseEntity.badRequest().body("Form not found or inactive.");
        }

        try {
            FormSubmission submission = submissionService.submitForm(definition, request, httpRequest);
            return ResponseEntity.ok(submission);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }
}

