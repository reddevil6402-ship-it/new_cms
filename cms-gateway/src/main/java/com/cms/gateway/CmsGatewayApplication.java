package com.cms.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * cms-gateway — Spring Cloud Gateway
 *
 * <p>This service is the single entry point for all external traffic.
 * It handles JWT validation, tenant status gating, and forwards requests
 * to the appropriate downstream service.
 *
 * <p><strong>Important:</strong> This service is WebFlux (reactive), not servlet-based.
 * Do not add any servlet-based dependencies (spring-boot-starter-web, Thymeleaf, etc.)
 * to this service's pom.xml. Static files are served from classpath:/static/.
 *
 * <p>Downstream services (ports):
 * <ul>
 *   <li>cms-iam-service    :8081</li>
 *   <li>cms-schema-service :8082</li>
 *   <li>cms-content-service:8083</li>
 *   <li>cms-workflow-service:8084</li>
 * </ul>
 */
@SpringBootApplication
public class CmsGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(CmsGatewayApplication.class, args);
    }
}
