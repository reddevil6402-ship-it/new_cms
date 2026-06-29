package com.cms.media.service;

import com.cms.common.exception.CmsException;
import com.cms.common.exception.ErrorCode;
import com.cms.common.security.TenantContext;
import com.cms.media.domain.MediaAsset;
import com.cms.media.dto.response.MediaResponse;
import com.cms.media.repository.MediaAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    private final MediaAssetRepository mediaAssetRepository;

    @Value("${cms.storage.local.upload-dir}")
    private String uploadDir;

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
    public MediaResponse uploadMedia(MultipartFile file, String altText, String caption) {
        UUID tenantId = getTenantId();

        if (file.isEmpty()) {
            throw new CmsException(ErrorCode.VALIDATION_ERROR, "File is empty");
        }

        try {
            // Ensure directory exists
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String storedName = UUID.randomUUID().toString() + extension;
            
            Path filePath = uploadPath.resolve(storedName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            MediaAsset asset = new MediaAsset();
            asset.setTenantId(tenantId);
            asset.setOriginalName(originalFilename != null ? originalFilename : "unknown");
            asset.setStoredName(storedName);
            asset.setStoragePath(filePath.toString());
            asset.setStorageBackend("LOCAL");
            asset.setMimeType(file.getContentType());
            asset.setFileSizeBytes(file.getSize());
            asset.setAltText(altText);
            asset.setCaption(caption);
            asset.setPublic(true);

            // In a real app we might extract width/height/duration here

            asset = mediaAssetRepository.save(asset);
            return mapToResponse(asset);

        } catch (IOException e) {
            log.error("Failed to store file", e);
            throw new CmsException(ErrorCode.INTERNAL_ERROR, "Failed to store file");
        }
    }

    @Transactional(readOnly = true)
    public List<MediaResponse> listMedia() {
        UUID tenantId = getTenantId();
        return mediaAssetRepository.findAllByTenantIdAndDeletedAtIsNull(tenantId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MediaResponse getMedia(UUID id) {
        UUID tenantId = getTenantId();
        MediaAsset asset = mediaAssetRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> new CmsException(ErrorCode.RESOURCE_NOT_FOUND, "Media not found"));
        return mapToResponse(asset);
    }

    @Transactional
    public void deleteMedia(UUID id) {
        UUID tenantId = getTenantId();
        MediaAsset asset = mediaAssetRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> new CmsException(ErrorCode.RESOURCE_NOT_FOUND, "Media not found"));
        
        asset.setDeletedAt(OffsetDateTime.now());
        mediaAssetRepository.save(asset);
    }

    private MediaResponse mapToResponse(MediaAsset asset) {
        MediaResponse response = new MediaResponse();
        response.setId(asset.getId());
        response.setOriginalName(asset.getOriginalName());
        response.setMimeType(asset.getMimeType());
        response.setFileSizeBytes(asset.getFileSizeBytes());
        response.setAltText(asset.getAltText());
        response.setCaption(asset.getCaption());
        response.setWidth(asset.getWidth());
        response.setHeight(asset.getHeight());
        response.setUploadedAt(asset.getUploadedAt());
        
        // Generate public URL using ServletUriComponentsBuilder
        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/uploads/")
                .path(asset.getStoredName())
                .toUriString();
        response.setUrl(url);
        
        return response;
    }
}
