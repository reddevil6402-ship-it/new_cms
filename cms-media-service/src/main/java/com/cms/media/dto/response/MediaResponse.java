package com.cms.media.dto.response;

import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;
import java.util.Map;

@Data
public class MediaResponse {
    private UUID id;
    private String originalName;
    private String mimeType;
    private long fileSizeBytes;
    private String altText;
    private String caption;
    private Integer width;
    private Integer height;
    private String url;
    private OffsetDateTime uploadedAt;
}
