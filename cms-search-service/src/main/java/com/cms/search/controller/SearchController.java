package com.cms.search.controller;

import com.cms.common.dto.ApiResponse;
import com.cms.search.dto.request.IndexRequest;
import com.cms.search.dto.response.SearchResponse;
import com.cms.search.service.SearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @PostMapping("/index")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> indexDocument(@Valid @RequestBody IndexRequest request) {
        searchService.indexDocument(request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/index/{id}")
    public ApiResponse<Void> deleteDocument(@PathVariable UUID id) {
        searchService.deleteDocument(id);
        return ApiResponse.ok(null);
    }

    @GetMapping
    public ApiResponse<SearchResponse> search(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "siteCode", required = false) String siteCode,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "contentTypeCode", required = false) String contentTypeCode) {
        SearchResponse response = searchService.search(query, siteCode, status, contentTypeCode);
        return ApiResponse.ok(response);
    }
}
