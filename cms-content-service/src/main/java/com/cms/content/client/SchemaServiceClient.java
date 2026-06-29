package com.cms.content.client;

import com.cms.common.dto.ApiResponse;
import com.cms.content.client.dto.SchemaResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaServiceClient {

    private final RestClient schemaRestClient;

    /**
     * Fetch schema definition by content type code.
     * RestClient automatically propagates X-Tenant-Id and Authorization bearer token.
     */
    public Optional<SchemaResponse> getSchemaByCode(String code) {
        log.info("Fetching schema definition for code: {}", code);
        try {
            ApiResponse<SchemaResponse> response = schemaRestClient.get()
                    .uri("/api/v1/schema/content-types/code/{code}", code)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<SchemaResponse>>() {});

            if (response != null && response.isSuccess() && response.getData() != null) {
                return Optional.of(response.getData());
            }
            log.warn("Schema service returned unsuccessful response or empty data for code: {}", code);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to retrieve schema definition from schema-service for code: {}", code, e);
            return Optional.empty();
        }
    }
}
