package com.cms.search.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DevTestController {

    @GetMapping("/dev/search")
    public String searchConsole() {
        return "search";
    }
}
