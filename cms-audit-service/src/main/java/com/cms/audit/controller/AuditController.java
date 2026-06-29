package com.cms.audit.controller;

import com.cms.common.dto.ApiResponse;
import com.cms.audit.domain.AuditEvent;
import com.cms.audit.dto.request.AuditEventRequest;
import com.cms.audit.service.AuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @PostMapping("/logs")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuditEvent> logEvent(@Valid @RequestBody AuditEventRequest request) {
        return ApiResponse.ok(auditService.logEvent(request));
    }

    @GetMapping("/logs")
    public ApiResponse<List<AuditEvent>> getLogs(
            @RequestParam(value = "entityType", required = false) String entityType,
            @RequestParam(value = "entityId", required = false) UUID entityId) {
        return ApiResponse.ok(auditService.getLogs(entityType, entityId));
    }
}
