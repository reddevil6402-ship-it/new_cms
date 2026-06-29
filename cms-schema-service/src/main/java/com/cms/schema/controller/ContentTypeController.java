package com.cms.schema.controller;

import com.cms.common.constants.CmsHeaders;
import com.cms.common.dto.ApiResponse;
import com.cms.common.exception.CmsException;
import com.cms.common.exception.ErrorCode;
import com.cms.schema.dto.request.ContentTypeRequest;
import com.cms.schema.dto.response.ContentTypeResponse;
import com.cms.schema.service.ContentTypeService;
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
@RequestMapping("/api/v1/schema/content-types")
@RequiredArgsConstructor
public class ContentTypeController {

    private final ContentTypeService contentTypeService;

    @PostMapping
    @PreAuthorize("hasAuthority('schema:CREATE:ALL')")
    public ResponseEntity<ApiResponse<ContentTypeResponse>> createContentType(
            @RequestHeader(CmsHeaders.X_TENANT_ID) String tenantIdStr,
            @RequestHeader(value = CmsHeaders.X_USER_ID, required = false) String userIdStr,
            @Valid @RequestBody ContentTypeRequest request) {

        UUID tenantId = parseUuid(tenantIdStr, "Tenant ID");
        UUID userId = userIdStr != null ? parseUuid(userIdStr, "User ID") : null;

        ContentTypeResponse response = contentTypeService.createContentType(tenantId, request, userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('schema:READ:ALL')")
    public ResponseEntity<ApiResponse<ContentTypeResponse>> getContentTypeById(
            @RequestHeader(CmsHeaders.X_TENANT_ID) String tenantIdStr,
            @PathVariable UUID id) {

        UUID tenantId = parseUuid(tenantIdStr, "Tenant ID");
        ContentTypeResponse response = contentTypeService.getContentTypeById(tenantId, id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/code/{code}")
    @PreAuthorize("hasAuthority('schema:READ:ALL')")
    public ResponseEntity<ApiResponse<ContentTypeResponse>> getContentTypeByCode(
            @RequestHeader(CmsHeaders.X_TENANT_ID) String tenantIdStr,
            @PathVariable String code) {

        UUID tenantId = parseUuid(tenantIdStr, "Tenant ID");
        ContentTypeResponse response = contentTypeService.getContentTypeByCode(tenantId, code);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('schema:READ:ALL')")
    public ResponseEntity<ApiResponse<List<ContentTypeResponse>>> getAllContentTypes(
            @RequestHeader(CmsHeaders.X_TENANT_ID) String tenantIdStr) {

        UUID tenantId = parseUuid(tenantIdStr, "Tenant ID");
        List<ContentTypeResponse> list = contentTypeService.getAllContentTypes(tenantId);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('schema:UPDATE:ALL')")
    public ResponseEntity<ApiResponse<ContentTypeResponse>> updateContentType(
            @RequestHeader(CmsHeaders.X_TENANT_ID) String tenantIdStr,
            @PathVariable UUID id,
            @Valid @RequestBody ContentTypeRequest request) {

        UUID tenantId = parseUuid(tenantIdStr, "Tenant ID");
        ContentTypeResponse response = contentTypeService.updateContentType(tenantId, id, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('schema:DELETE:ALL')")
    public ResponseEntity<ApiResponse<Void>> deleteContentType(
            @RequestHeader(CmsHeaders.X_TENANT_ID) String tenantIdStr,
            @PathVariable UUID id) {

        UUID tenantId = parseUuid(tenantIdStr, "Tenant ID");
        contentTypeService.deleteContentType(tenantId, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    private UUID parseUuid(String uuidStr, String fieldName) {
        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            throw new CmsException(ErrorCode.VALIDATION_ERROR, "Invalid " + fieldName + " format: " + uuidStr);
        }
    }
}
