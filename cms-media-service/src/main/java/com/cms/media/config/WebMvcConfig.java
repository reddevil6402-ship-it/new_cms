package com.cms.media.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${cms.storage.local.upload-dir}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get(uploadDir);
        String uploadAbsolutePath = uploadPath.toFile().getAbsolutePath();
        
        // Ensure path ends with slash for ResourceHandler
        if (!uploadAbsolutePath.endsWith("/") && !uploadAbsolutePath.endsWith("\\")) {
            uploadAbsolutePath += "/";
        }

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:///" + uploadAbsolutePath);
    }
}
