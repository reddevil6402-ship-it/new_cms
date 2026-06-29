package com.cms.form.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/dev")
public class DevTestController {

    @GetMapping("/form")
    public String formTest(Model model) {
        model.addAttribute("text", "JWT Token for testing can be provided in the UI headers.");
        return "dev/form";
    }
}
