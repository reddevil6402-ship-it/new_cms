package com.cms.iam.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Dev-only controller serving Thymeleaf test pages for the IAM service.
 *
 * These pages test all IAM endpoints directly from the browser using JavaScript
 * fetch() calls, which means cookies behave exactly as a real browser client would.
 *
 * REMOVE THIS CONTROLLER (and the /dev/** security exception) before production.
 */
@Controller
@RequestMapping("/dev")
public class DevTestController {

    @GetMapping({"", "/"})
    public String index() {
        return "dev/index";
    }

    @GetMapping("/auth")
    public String auth() {
        return "dev/auth";
    }
}
