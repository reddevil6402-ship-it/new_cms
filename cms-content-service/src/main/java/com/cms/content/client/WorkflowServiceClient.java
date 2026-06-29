package com.cms.content.client;

import com.cms.common.dto.ApiResponse;
import com.cms.content.client.dto.WorkflowInstanceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowServiceClient {

    private final RestClient workflowRestClient;

    /**
     * Start a new workflow instance for a content item.
     */
    public Optional<WorkflowInstanceDto> startWorkflowInstance(String workflowCode, UUID contentId) {
        log.info("Requesting start of workflow {} for content item: {}", workflowCode, contentId);
        
        Map<String, Object> request = Map.of(
                "workflowCode", workflowCode,
                "entityType", "content_item",
                "entityId", contentId.toString()
        );

        try {
            ApiResponse<WorkflowInstanceDto> response = workflowRestClient.post()
                    .uri("/api/v1/workflow/instances")
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<WorkflowInstanceDto>>() {});

            if (response != null && response.isSuccess() && response.getData() != null) {
                return Optional.of(response.getData());
            }
            log.warn("Workflow service returned unsuccessful response for code: {}", workflowCode);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to start workflow instance from workflow-service for code: {}", workflowCode, e);
            return Optional.empty();
        }
    }
}
