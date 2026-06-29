package com.cms.workflow.dto.response;

import com.cms.workflow.domain.WorkflowHistory;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class WorkflowHistoryResponse {

    private UUID id;
    private String fromState;
    private String toState;
    private String trigger;
    private UUID actorId;
    private String actorEmail;
    private String comment;
    private OffsetDateTime occurredAt;
    private Map<String, Object> metadata;

    public static WorkflowHistoryResponse fromEntity(WorkflowHistory entity) {
        if (entity == null) return null;
        WorkflowHistoryResponse resp = new WorkflowHistoryResponse();
        resp.setId(entity.getId());
        resp.setFromState(entity.getFromState());
        resp.setToState(entity.getToState());
        resp.setTrigger(entity.getTrigger());
        resp.setActorId(entity.getActorId());
        resp.setActorEmail(entity.getActorEmail());
        resp.setComment(entity.getComment());
        resp.setOccurredAt(entity.getOccurredAt());
        resp.setMetadata(entity.getMetadata());
        return resp;
    }
}
