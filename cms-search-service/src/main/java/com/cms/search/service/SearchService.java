package com.cms.search.service;

import com.cms.common.exception.CmsException;
import com.cms.common.exception.ErrorCode;
import com.cms.common.security.TenantContext;
import com.cms.search.dto.request.IndexRequest;
import com.cms.search.dto.response.SearchResponse;
import com.cms.search.dto.response.SearchResponse.SearchResultHit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final OpenSearchClient openSearchClient;

    private void ensureIndexExists(String indexName) throws IOException {
        boolean exists = openSearchClient.indices().exists(e -> e.index(indexName)).value();
        if (!exists) {
            openSearchClient.indices().create(c -> c.index(indexName));
            log.info("Created OpenSearch index: {}", indexName);
        }
    }

    public void indexDocument(IndexRequest request) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            throw new CmsException(ErrorCode.ACCESS_DENIED, "Tenant context is missing");
        }
        String indexName = "cms-content-" + tenantId.toLowerCase();

        try {
            ensureIndexExists(indexName);

            IndexResponse response = openSearchClient.index(i -> i
                    .index(indexName)
                    .id(request.getId().toString())
                    .document(request)
            );
            log.info("Successfully indexed document {} in index {}. Result: {}", request.getId(), indexName, response.result());
        } catch (IOException e) {
            log.error("Failed to index document in OpenSearch", e);
            throw new CmsException(ErrorCode.INTERNAL_ERROR, "Failed to index document: " + e.getMessage());
        }
    }

    public void deleteDocument(UUID id) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            throw new CmsException(ErrorCode.ACCESS_DENIED, "Tenant context is missing");
        }
        String indexName = "cms-content-" + tenantId.toLowerCase();

        try {
            openSearchClient.delete(d -> d.index(indexName).id(id.toString()));
            log.info("Successfully deleted document {} from index {}", id, indexName);
        } catch (IOException e) {
            log.error("Failed to delete document from OpenSearch", e);
            throw new CmsException(ErrorCode.INTERNAL_ERROR, "Failed to delete document: " + e.getMessage());
        }
    }

    public SearchResponse search(String query, String siteCode, String status, String contentTypeCode) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            throw new CmsException(ErrorCode.ACCESS_DENIED, "Tenant context is missing");
        }
        String indexName = "cms-content-" + tenantId.toLowerCase();

        try {
            ensureIndexExists(indexName);

            org.opensearch.client.opensearch.core.SearchResponse<IndexRequest> response = openSearchClient.search(s -> s
                    .index(indexName)
                    .query(q -> q
                            .bool(b -> {
                                // Multi-match search across title, tags, and body fields
                                if (query != null && !query.isBlank()) {
                                    b.must(m -> m
                                            .multiMatch(mm -> mm
                                                    .query(query)
                                                    .fields(List.of("title^3", "tags^2", "body.*"))
                                            )
                                    );
                                } else {
                                    b.must(m -> m.matchAll(ma -> ma));
                                }

                                // Filters
                                if (siteCode != null && !siteCode.isBlank()) {
                                    b.filter(f -> f.term(t -> t.field("siteCode.keyword").value(v -> v.stringValue(siteCode))));
                                }
                                if (status != null && !status.isBlank()) {
                                    b.filter(f -> f.term(t -> t.field("status.keyword").value(v -> v.stringValue(status))));
                                }
                                if (contentTypeCode != null && !contentTypeCode.isBlank()) {
                                    b.filter(f -> f.term(t -> t.field("contentTypeCode.keyword").value(v -> v.stringValue(contentTypeCode))));
                                }

                                return b;
                            })
                    ),
                    IndexRequest.class
            );

            List<SearchResultHit> hits = new ArrayList<>();
            for (Hit<IndexRequest> hit : response.hits().hits()) {
                IndexRequest doc = hit.source();
                if (doc != null) {
                    hits.add(SearchResultHit.builder()
                            .id(doc.getId().toString())
                            .contentTypeCode(doc.getContentTypeCode())
                            .title(doc.getTitle())
                            .slug(doc.getSlug())
                            .status(doc.getStatus())
                            .categoryCode(doc.getCategoryCode())
                            .siteCode(doc.getSiteCode())
                            .tags(doc.getTags())
                            .body(doc.getBody())
                            .build()
                    );
                }
            }

            return SearchResponse.builder()
                    .hits(hits)
                    .totalHits(response.hits().total() != null ? response.hits().total().value() : 0)
                    .build();

        } catch (IOException e) {
            log.error("Failed to query OpenSearch", e);
            throw new CmsException(ErrorCode.INTERNAL_ERROR, "Failed to query OpenSearch: " + e.getMessage());
        }
    }
}
