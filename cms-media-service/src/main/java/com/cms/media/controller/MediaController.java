package com.cms.media.controller;

import com.cms.common.dto.ApiResponse;
import com.cms.media.dto.response.MediaResponse;
import com.cms.media.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MediaResponse> uploadMedia(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "altText", required = false) String altText,
            @RequestParam(value = "caption", required = false) String caption) {
        MediaResponse response = mediaService.uploadMedia(file, altText, caption);
        return ApiResponse.ok(response);
    }

    @GetMapping
    public ApiResponse<List<MediaResponse>> listMedia() {
        return ApiResponse.ok(mediaService.listMedia());
    }

    @GetMapping("/{id}")
    public ApiResponse<MediaResponse> getMedia(@PathVariable UUID id) {
        return ApiResponse.ok(mediaService.getMedia(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteMedia(@PathVariable UUID id) {
        mediaService.deleteMedia(id);
        return ApiResponse.ok(null);
    }
}
