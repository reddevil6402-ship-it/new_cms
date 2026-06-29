package com.cms.content.controller;

import com.cms.common.constants.CmsHeaders;
import com.cms.common.dto.ApiResponse;
import com.cms.common.exception.CmsException;
import com.cms.common.exception.ErrorCode;
import com.cms.content.dto.request.ContentRequest;
import com.cms.content.dto.response.ContentResponse;
import com.cms.content.dto.response.ContentVersionResponse;
import com.cms.content.service.ContentService;
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
@RequestMapping("/api/v1/content")
@RequiredArgsConstructor
public class ContentController {

    private final ContentService contentService;

    @PostMapping
    @PreAuthorize("hasAuthority('content:CREATE:ALL') or hasAuthority('content:CREATE:OWN')")
    public ResponseEntity<ApiResponse<ContentResponse>> createContentItem(
            @RequestHeader(CmsHeaders.X_TENANT_ID) String tenantIdStr,
            @RequestHeader(value = CmsHeaders.X_USER_ID, required = false) String userIdStr,
            @Valid @RequestBody ContentRequest request) {

        UUID tenantId = parseUuid(tenantIdStr, "Tenant ID");
        UUID userId = userIdStr != null ? parseUuid(userIdStr, "User ID") : null;

        ContentResponse response = contentService.createContentItem(tenantId, request, userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('content:READ:ALL') or hasAuthority('content:READ:OWN')")
    public ResponseEntity<ApiResponse<ContentResponse>> getContentItemById(
            @RequestHeader(CmsHeaders.X_TENANT_ID) String tenantIdStr,
            @PathVariable UUID id) {

        UUID tenantId = parseUuid(tenantIdStr, "Tenant ID");
        ContentResponse response = contentService.getContentItemById(tenantId, id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/type/{typeCode}")
    @PreAuthorize("hasAuthority('content:READ:ALL') or hasAuthority('content:READ:OWN')")
    public ResponseEntity<ApiResponse<List<ContentResponse>>> getContentItemsByTypeCode(
            @RequestHeader(CmsHeaders.X_TENANT_ID) String tenantIdStr,
            @PathVariable String typeCode) {

        UUID tenantId = parseUuid(tenantIdStr, "Tenant ID");
        List<ContentResponse> response = contentService.getContentItemsByTypeCode(tenantId, typeCode);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('content:READ:ALL') or hasAuthority('content:READ:OWN')")
    public ResponseEntity<ApiResponse<List<ContentResponse>>> getAllContentItems(
            @RequestHeader(CmsHeaders.X_TENANT_ID) String tenantIdStr) {

        UUID tenantId = parseUuid(tenantIdStr, "Tenant ID");
        List<ContentResponse> response = contentService.getAllContentItems(tenantId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('content:UPDATE:ALL') or hasAuthority('content:UPDATE:OWN')")
    public ResponseEntity<ApiResponse<ContentResponse>> updateContentItem(
            @RequestHeader(CmsHeaders.X_TENANT_ID) String tenantIdStr,
            @RequestHeader(value = CmsHeaders.X_USER_ID, required = false) String userIdStr,
            @PathVariable UUID id,
            @Valid @RequestBody ContentRequest request) {

        UUID tenantId = parseUuid(tenantIdStr, "Tenant ID");
        UUID userId = userIdStr != null ? parseUuid(userIdStr, "User ID") : null;

        ContentResponse response = contentService.updateContentItem(tenantId, id, request, userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('content:DELETE:ALL') or hasAuthority('content:DELETE:OWN')")
    public ResponseEntity<ApiResponse<Void>> deleteContentItem(
            @RequestHeader(CmsHeaders.X_TENANT_ID) String tenantIdStr,
            @RequestHeader(value = CmsHeaders.X_USER_ID, required = false) String userIdStr,
            @PathVariable UUID id) {

        UUID tenantId = parseUuid(tenantIdStr, "Tenant ID");
        UUID userId = userIdStr != null ? parseUuid(userIdStr, "User ID") : null;

        contentService.deleteContentItem(tenantId, id, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('content:READ:ALL') or hasAuthority('content:READ:OWN')")
    public ResponseEntity<ApiResponse<List<ContentVersionResponse>>> getContentVersions(
            @RequestHeader(CmsHeaders.X_TENANT_ID) String tenantIdStr,
            @PathVariable UUID id) {

        UUID tenantId = parseUuid(tenantIdStr, "Tenant ID");
        List<ContentVersionResponse> response = contentService.getContentVersions(tenantId, id);
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
