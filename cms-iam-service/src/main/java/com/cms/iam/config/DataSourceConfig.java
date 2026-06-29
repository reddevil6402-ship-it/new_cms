package com.cms.iam.config;

import com.cms.common.security.TenantAwareDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.hikari.maximum-pool-size:10}")
    private int maxPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle:2}")
    private int minIdle;

    /**
     * Wraps HikariCP with {@link TenantAwareDataSource} so that every connection
     * checkout executes {@code SET LOCAL app.current_tenant_id = ?}.
     *
     * <p>NOTE: iam-service is the identity authority — most of its queries are
     * system-level (login, token validation) and run without tenant context.
     * {@link com.cms.common.security.TenantAwareDataSource} is a no-op when
     * {@link com.cms.common.security.TenantContext#getCurrentTenantId()} returns null,
     * so this is safe for system-level operations.
     */
    @Bean
    @Primary
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setPoolName("IamHikariPool");
        config.setConnectionTestQuery("SELECT 1");

        return new TenantAwareDataSource(new HikariDataSource(config));
    }
}
