package com.cms.content.dto.response;

import com.cms.content.domain.ContentVersion;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class ContentVersionResponse {

    private UUID id;
    private UUID contentItemId;
    private int versionNumber;
    private String title;
    private Map<String, Object> body;
    private String status;
    private String changeSummary;
    private OffsetDateTime createdAt;
    private UUID createdBy;

    public static ContentVersionResponse fromEntity(ContentVersion entity) {
        if (entity == null) return null;
        ContentVersionResponse resp = new ContentVersionResponse();
        resp.setId(entity.getId());
        resp.setContentItemId(entity.getContentItem().getId());
        resp.setVersionNumber(entity.getVersionNumber());
        resp.setTitle(entity.getTitle());
        resp.setBody(entity.getBody());
        resp.setStatus(entity.getStatus());
        resp.setChangeSummary(entity.getChangeSummary());
        resp.setCreatedAt(entity.getCreatedAt());
        resp.setCreatedBy(entity.getCreatedBy());
        return resp;
    }
}
