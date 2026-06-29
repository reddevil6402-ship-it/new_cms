package com.cms.audit.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DevTestController {

    @GetMapping("/dev/audit")
    public String auditConsole() {
        return "audit";
    }
}
