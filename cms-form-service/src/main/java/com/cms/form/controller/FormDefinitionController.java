package com.cms.form.controller;

import com.cms.form.domain.FormDefinition;
import com.cms.form.service.FormDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/forms/definitions")
@RequiredArgsConstructor
public class FormDefinitionController {

    private final FormDefinitionService service;

    @PostMapping
    public ResponseEntity<FormDefinition> createForm(@RequestBody FormDefinition form) {
        return ResponseEntity.ok(service.createForm(form));
    }

    @GetMapping
    public ResponseEntity<List<FormDefinition>> getAllForms() {
        return ResponseEntity.ok(service.getAllForms());
    }

    @GetMapping("/{code}")
    public ResponseEntity<FormDefinition> getForm(@PathVariable String code) {
        return service.getFormByCode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
