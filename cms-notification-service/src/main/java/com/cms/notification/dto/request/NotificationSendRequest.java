package com.cms.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.Map;

@Data
public class NotificationSendRequest {
    @NotBlank
    private String templateCode;
    
    @NotBlank
    private String recipient;
    
    @NotBlank
    private String channel;
    
    private Map<String, Object> payload;
}
