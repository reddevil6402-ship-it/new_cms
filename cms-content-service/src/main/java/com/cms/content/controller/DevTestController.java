package com.cms.content.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Dev-only controller serving Thymeleaf test pages for the Content service.
 *
 * Exposes /dev/content.
 */
@Controller
@RequestMapping("/dev")
public class DevTestController {

    @GetMapping("/content")
    public String contentConsole() {
        return "dev/content";
    }
}
