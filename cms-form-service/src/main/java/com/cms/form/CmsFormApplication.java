package com.cms.form;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class CmsFormApplication {
    public static void main(String[] args) {
        SpringApplication.run(CmsFormApplication.class, args);
    }
}
