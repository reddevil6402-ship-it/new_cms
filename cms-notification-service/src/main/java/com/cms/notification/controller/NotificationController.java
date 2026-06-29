package com.cms.notification.controller;

import com.cms.common.dto.ApiResponse;
import com.cms.notification.domain.NotificationLog;
import com.cms.notification.domain.NotificationTemplate;
import com.cms.notification.dto.request.NotificationSendRequest;
import com.cms.notification.dto.request.NotificationTemplateRequest;
import com.cms.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<NotificationTemplate> createTemplate(@Valid @RequestBody NotificationTemplateRequest request) {
        return ApiResponse.ok(notificationService.createTemplate(request));
    }

    @GetMapping("/templates")
    public ApiResponse<List<NotificationTemplate>> listTemplates() {
        return ApiResponse.ok(notificationService.listTemplates());
    }

    @PostMapping("/send")
    public ApiResponse<NotificationLog> sendNotification(@Valid @RequestBody NotificationSendRequest request) {
        return ApiResponse.ok(notificationService.sendNotification(request));
    }

    @GetMapping("/logs")
    public ApiResponse<List<NotificationLog>> getLogs() {
        return ApiResponse.ok(notificationService.getLogs());
    }
}
