package com.cms.notification.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DevTestController {

    @GetMapping("/dev/notification")
    public String notificationConsole() {
        return "notification";
    }
}
