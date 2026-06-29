package com.cms.workflow.service;

import com.cms.common.exception.CmsException;
import com.cms.common.exception.ErrorCode;
import com.cms.workflow.domain.WorkflowDefinition;
import com.cms.workflow.domain.WorkflowInstance;
import com.cms.workflow.domain.WorkflowHistory;
import com.cms.workflow.dto.request.WorkflowDefinitionRequest;
import com.cms.workflow.dto.request.WorkflowInstanceRequest;
import com.cms.workflow.dto.request.WorkflowTransitionRequest;
import com.cms.workflow.dto.response.WorkflowDefinitionResponse;
import com.cms.workflow.dto.response.WorkflowInstanceResponse;
import com.cms.workflow.dto.response.WorkflowHistoryResponse;
import com.cms.workflow.repository.WorkflowDefinitionRepository;
import com.cms.workflow.repository.WorkflowInstanceRepository;
import com.cms.workflow.repository.WorkflowHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowHistoryRepository historyRepository;

    @Transactional
    public WorkflowDefinitionResponse createDefinition(UUID tenantId, WorkflowDefinitionRequest request) {
        log.info("Creating workflow definition {} for tenant {}", request.getCode(), tenantId);

        // Get latest version and increment
        int nextVersion = 1;
        Optional<WorkflowDefinition> latest = definitionRepository
                .findFirstByTenantIdAndCodeOrderByVersionDesc(tenantId, request.getCode());
        if (latest.isPresent()) {
            nextVersion = latest.get().getVersion() + 1;
            // Deactivate previous versions
            latest.get().setActive(false);
            definitionRepository.save(latest.get());
        }

        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setTenantId(tenantId);
        definition.setCode(request.getCode());
        definition.setName(request.getName());
        definition.setDescription(request.getDescription());
        definition.setDefinition(request.getDefinition());
        definition.setVersion(nextVersion);
        definition.setActive(true);

        WorkflowDefinition saved = definitionRepository.save(definition);
        return WorkflowDefinitionResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<WorkflowDefinitionResponse> getActiveDefinitions(UUID tenantId) {
        return definitionRepository.findAllByTenantIdAndIsActiveTrue(tenantId).stream()
                .map(WorkflowDefinitionResponse::fromEntity)
                .toList();
    }

    @Transactional
    public WorkflowInstanceResponse startInstance(UUID tenantId, WorkflowInstanceRequest request, UUID actorId, String actorEmail) {
        log.info("Starting workflow instance {} for entity {}/{}", request.getWorkflowCode(), request.getEntityType(), request.getEntityId());

        WorkflowDefinition definition = definitionRepository
                .findFirstByTenantIdAndCodeOrderByVersionDesc(tenantId, request.getWorkflowCode())
                .orElseThrow(() -> new CmsException(ErrorCode.WORKFLOW_NOT_FOUND,
                        "Active workflow definition with code '" + request.getWorkflowCode() + "' not found"));

        // Check if there is already an active instance for this entity
        Optional<WorkflowInstance> existing = instanceRepository
                .findByEntityTypeAndEntityId(request.getEntityType(), request.getEntityId());
        if (existing.isPresent() && existing.get().getCompletedAt() == null) {
            throw new CmsException(ErrorCode.WORKFLOW_ALREADY_ASSIGNED,
                    "An active workflow instance already exists for entity " + request.getEntityId());
        }

        String initialState = definition.getDefinition().getInitialState();
        if (initialState == null || initialState.isBlank()) {
            throw new CmsException(ErrorCode.INTERNAL_ERROR, "Workflow definition has no initial state configured");
        }

        WorkflowInstance instance = new WorkflowInstance();
        instance.setWorkflowDefinition(definition);
        instance.setWorkflowDefVersion(definition.getVersion());
        instance.setEntityType(request.getEntityType());
        instance.setEntityId(request.getEntityId());
        instance.setCurrentState(initialState);
        instance.setMetadata(request.getMetadata());

        // Create initial log entry
        WorkflowHistory initialHistory = new WorkflowHistory();
        initialHistory.setFromState("NONE");
        initialHistory.setToState(initialState);
        initialHistory.setTrigger("START");
        initialHistory.setActorId(actorId);
        initialHistory.setActorEmail(actorEmail);
        initialHistory.setComment("Workflow initiated.");
        instance.addHistory(initialHistory);

        WorkflowInstance saved = instanceRepository.save(instance);
        return WorkflowInstanceResponse.fromEntity(saved);
    }

    @Transactional
    public WorkflowInstanceResponse executeTransition(UUID tenantId, UUID instanceId, WorkflowTransitionRequest request, UUID actorId, String actorEmail) {
        log.info("Executing transition trigger {} for workflow instance {}", request.getTrigger(), instanceId);

        WorkflowInstance instance = instanceRepository.findByTenantIdAndId(tenantId, instanceId)
                .orElseThrow(() -> new CmsException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Workflow instance with id '" + instanceId + "' not found or access is denied"));

        if (instance.getCompletedAt() != null) {
            throw new CmsException(ErrorCode.INVALID_WORKFLOW_TRANSITION,
                    "Cannot trigger transitions on a completed workflow instance");
        }

        WorkflowDefinition definition = instance.getWorkflowDefinition();
        String currentState = instance.getCurrentState();

        // Find transition matching trigger and fromState
        WorkflowDefinition.Transition transition = definition.getDefinition().getTransitions().stream()
                .filter(t -> t.getFromState().equalsIgnoreCase(currentState) && t.getTrigger().equalsIgnoreCase(request.getTrigger()))
                .findFirst()
                .orElseThrow(() -> new CmsException(ErrorCode.INVALID_WORKFLOW_TRANSITION,
                        "Trigger '" + request.getTrigger() + "' is not a valid transition from state '" + currentState + "'"));

        // Update state
        String targetState = transition.getToState();
        instance.setCurrentState(targetState);

        // Record history log
        WorkflowHistory history = new WorkflowHistory();
        history.setFromState(currentState);
        history.setToState(targetState);
        history.setTrigger(request.getTrigger());
        history.setActorId(actorId);
        history.setActorEmail(actorEmail);
        history.setComment(request.getComment());
        history.setMetadata(request.getMetadata());
        instance.addHistory(history);

        // Check if final state reached (simple heuristic: if no further transitions emerge from this state, it's final)
        boolean hasTransitionsOut = definition.getDefinition().getTransitions().stream()
                .anyMatch(t -> t.getFromState().equalsIgnoreCase(targetState));
        if (!hasTransitionsOut) {
            instance.setCompletedAt(OffsetDateTime.now());
            log.info("Workflow instance {} completed at state {}", instanceId, targetState);
        }

        WorkflowInstance saved = instanceRepository.save(instance);
        return WorkflowInstanceResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<WorkflowHistoryResponse> getInstanceHistory(UUID tenantId, UUID instanceId) {
        // Verify tenant owns the instance
        WorkflowInstance instance = instanceRepository.findByTenantIdAndId(tenantId, instanceId)
                .orElseThrow(() -> new CmsException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Workflow instance with id '" + instanceId + "' not found or access is denied"));

        return historyRepository.findAllByWorkflowInstanceIdOrderByOccurredAtDesc(instance.getId()).stream()
                .map(WorkflowHistoryResponse::fromEntity)
                .toList();
    }
}
