package com.cms.workflow.controller;

import com.cms.common.constants.CmsHeaders;
import com.cms.common.dto.ApiResponse;
import com.cms.common.exception.CmsException;
import com.cms.common.exception.ErrorCode;
import com.cms.workflow.dto.request.WorkflowDefinitionRequest;
import com.cms.workflow.dto.request.WorkflowInstanceRequest;
import com.cms.workflow.dto.request.WorkflowTransitionRequest;
import com.cms.workflow.dto.response.WorkflowDefinitionResponse;
import com.cms.workflow.dto.response.WorkflowInstanceResponse;
import com.cms.workflow.dto.response.WorkflowHistoryResponse;
import com.cms.workflow.service.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @PostMapping("/definitions")
    @PreAuthorize("hasAuthority('workflow:CREATE:ALL')")
    public ResponseEntity<ApiResponse<WorkflowDefinitionResponse>> createDefinition(
            @RequestHeader(CmsHeaders.X_TENANT_ID) String tenantIdStr,
            @Valid @RequestBody WorkflowDefinitionRequest request) {

        UUID tenantId = parseUuid(tenantIdStr, "Tenant ID");
        WorkflowDefinitionResponse response = workflowService.createDefinition(tenantId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/definitions")
    @PreAuthorize("hasAuthority('workflow:READ:ALL')")
    public ResponseEntity<ApiResponse<List<WorkflowDefinitionResponse>>> getActiveDefinitions(
            @RequestHeader(CmsHeaders.X_TENANT_ID) String tenantIdStr) {

        UUID tenantId = parseUuid(tenantIdStr, "Tenant ID");
        List<WorkflowDefinitionResponse> response = workflowService.getActiveDefinitions(tenantId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/instances")
    @PreAuthorize("hasAuthority('workflow:EXECUTE:ALL')")
    public ResponseEntity<ApiResponse<WorkflowInstanceResponse>> startInstance(
            @RequestHeader(CmsHeaders.X_TENANT_ID) String tenantIdStr,
            @RequestHeader(value = CmsHeaders.X_USER_ID, required = false) String userIdStr,
            @RequestHeader(value = "X-User-Email", required = false) String actorEmail,
            @Valid @RequestBody WorkflowInstanceRequest request) {

        UUID tenantId = parseUuid(tenantIdStr, "Tenant ID");
        UUID actorId = userIdStr != null ? parseUuid(userIdStr, "User/Actor ID") : null;

        WorkflowInstanceResponse response = workflowService.startInstance(tenantId, request, actorId, actorEmail);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/instances/{id}/transition")
    @PreAuthorize("hasAuthority('workflow:EXECUTE:ALL')")
    public ResponseEntity<ApiResponse<WorkflowInstanceResponse>> executeTransition(
            @RequestHeader(CmsHeaders.X_TENANT_ID) String tenantIdStr,
            @RequestHeader(value = CmsHeaders.X_USER_ID, required = false) String userIdStr,
            @RequestHeader(value = "X-User-Email", required = false) String actorEmail,
            @PathVariable UUID id,
            @Valid @RequestBody WorkflowTransitionRequest request) {

        UUID tenantId = parseUuid(tenantIdStr, "Tenant ID");
        UUID actorId = userIdStr != null ? parseUuid(userIdStr, "User/Actor ID") : null;

        WorkflowInstanceResponse response = workflowService.executeTransition(tenantId, id, request, actorId, actorEmail);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/instances/{id}/history")
    @PreAuthorize("hasAuthority('workflow:READ:ALL')")
    public ResponseEntity<ApiResponse<List<WorkflowHistoryResponse>>> getInstanceHistory(
            @RequestHeader(CmsHeaders.X_TENANT_ID) String tenantIdStr,
            @PathVariable UUID id) {

        UUID tenantId = parseUuid(tenantIdStr, "Tenant ID");
        List<WorkflowHistoryResponse> response = workflowService.getInstanceHistory(tenantId, id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    private UUID parseUuid(String uuidStr, String fieldName) {
        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            throw new CmsException(ErrorCode.VALIDATION_ERROR, "Invalid " + fieldName + " format: " + uuidStr);
        }
    }
}
