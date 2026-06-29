package com.cms.search.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class IndexRequest {
    @NotNull
    private UUID id;
    
    @NotBlank
    private String contentTypeCode;
    
    @NotBlank
    private String title;
    
    private String slug;
    private Map<String, Object> body;
    
    @NotBlank
    private String status;
    
    private String categoryCode;
    
    @NotBlank
    private String siteCode;
    
    private List<String> tags;
    private String publishedAt;
}
