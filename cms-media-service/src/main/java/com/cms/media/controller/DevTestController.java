package com.cms.media.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DevTestController {

    @GetMapping("/dev/media")
    public String mediaConsole() {
        return "media";
    }
}
