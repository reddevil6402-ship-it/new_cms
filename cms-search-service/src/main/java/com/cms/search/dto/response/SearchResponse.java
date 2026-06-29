package com.cms.search.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class SearchResponse {
    private List<SearchResultHit> hits;
    private long totalHits;

    @Data
    @Builder
    public static class SearchResultHit {
        private String id;
        private String contentTypeCode;
        private String title;
        private String slug;
        private String status;
        private String categoryCode;
        private String siteCode;
        private List<String> tags;
        private Map<String, Object> body;
    }
}
