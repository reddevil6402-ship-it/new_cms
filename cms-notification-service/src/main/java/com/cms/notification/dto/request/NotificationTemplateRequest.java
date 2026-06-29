package com.cms.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NotificationTemplateRequest {
    @NotBlank
    private String code;
    
    @NotBlank
    private String name;
    
    @NotBlank
    private String channel;
    
    private String subject;
    
    @NotBlank
    private String bodyTemplate;
}
