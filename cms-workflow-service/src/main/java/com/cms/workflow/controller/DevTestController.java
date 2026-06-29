package com.cms.workflow.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Dev-only controller serving Thymeleaf test pages for the Workflow service.
 *
 * Exposes /dev/workflow.
 */
@Controller
@RequestMapping("/dev")
public class DevTestController {

    @GetMapping("/workflow")
    public String workflowConsole() {
        return "dev/workflow";
    }
}
