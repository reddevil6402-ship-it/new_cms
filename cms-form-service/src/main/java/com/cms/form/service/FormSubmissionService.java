package com.cms.form.service;

import com.cms.form.domain.FormDefinition;
import com.cms.form.domain.FormSubmission;
import com.cms.form.dto.request.FormSubmitRequest;
import com.cms.form.repository.FormSubmissionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FormSubmissionService {

    private final FormSubmissionRepository submissionRepository;
    private final ObjectMapper objectMapper;

    public FormSubmission submitForm(FormDefinition definition, FormSubmitRequest request, HttpServletRequest httpRequest) {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        JsonSchema schema = factory.getSchema(definition.getSchema());

        JsonNode dataNode = objectMapper.valueToTree(request.getData());
        Set<ValidationMessage> errors = schema.validate(dataNode);

        if (!errors.isEmpty()) {
            String errorMsg = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Validation failed: " + errorMsg);
        }

        FormSubmission submission = new FormSubmission();
        submission.setFormDefinition(definition);
        
        try {
            submission.setSubmittedData(objectMapper.writeValueAsString(request.getData()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error processing JSON", e);
        }

        String userIdHeader = httpRequest.getHeader("X-User-Id");
        if (userIdHeader != null && !userIdHeader.isEmpty()) {
            submission.setSubmittedBy(UUID.fromString(userIdHeader));
        }

        String forwardedFor = httpRequest.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            submission.setIpAddress(forwardedFor.split(",")[0].trim());
        } else {
            submission.setIpAddress(httpRequest.getRemoteAddr());
        }

        submission.setUserAgent(httpRequest.getHeader("User-Agent"));

        return submissionRepository.save(submission);
    }
}
