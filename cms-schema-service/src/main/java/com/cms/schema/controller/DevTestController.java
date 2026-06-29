package com.cms.schema.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Dev-only controller serving Thymeleaf test pages for the Schema service.
 *
 * <p>Exposed at /dev/schema.
 */
@Controller
@RequestMapping("/dev")
public class DevTestController {

    @GetMapping("/schema")
    public String schemaConsole() {
        return "dev/schema";
    }
}
