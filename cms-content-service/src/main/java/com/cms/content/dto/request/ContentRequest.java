package com.cms.content.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class ContentRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String slug;

    @NotBlank(message = "Content type code is required")
    private String contentTypeCode;

    private UUID categoryId;
    private String categoryCode;

    private UUID parentId;

    @NotNull(message = "Site ID is required")
    private UUID siteId;

    @NotBlank(message = "Site code is required")
    private String siteCode;

    private String persona;

    private Map<String, Object> body = Map.of();

    private List<UUID> mediaIds = new ArrayList<>();
    private List<String> tags = new ArrayList<>();
    private String externalLink;
    private int orderIndex = 0;
    private boolean featured = false;
    private Map<String, Object> metadata = Map.of();

    // Default publishing workflow used if none exists
    private String workflowCode = "default_publishing";

    private String changeSummary;
}
