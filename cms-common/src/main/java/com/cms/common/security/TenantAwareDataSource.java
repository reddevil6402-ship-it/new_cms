package com.cms.common.security;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * HikariCP DataSource wrapper that injects the current tenant ID as a
 * PostgreSQL session variable on every connection checkout.
 *
 * <h2>Why {@code SET LOCAL} — not {@code SET}</h2>
 * <p>{@code SET} persists the variable for the lifetime of the connection.
 * When HikariCP returns a connection to the pool and reuses it for a different
 * request (possibly a different tenant), the old tenant ID is still set.
 * This is a critical multi-tenant data isolation failure.
 *
 * <p>{@code SET LOCAL} scopes the variable to the current transaction only.
 * PostgreSQL automatically clears it on {@code COMMIT} or {@code ROLLBACK},
 * making it pool-safe by design — no manual cleanup required.
 *
 * <h2>Null safety</h2>
 * <p>If {@link TenantContext#getCurrentTenantId()} returns {@code null}
 * (e.g., during Flyway migrations, health checks, or internal system tasks),
 * the {@code SET LOCAL} is skipped entirely. PostgreSQL RLS policies that
 * reference {@code current_setting('app.current_tenant_id', true)} will
 * receive an empty string and should be written to handle that case
 * (e.g., block all rows for unauthenticated access).
 *
 * <h2>Usage</h2>
 * <p>Each service declares a {@code DataSourceConfig} that wraps HikariCP:
 * <pre>{@code
 * @Configuration
 * public class DataSourceConfig {
 *
 *     @Bean
 *     @Primary
 *     public DataSource dataSource(
 *             @Value("${spring.datasource.url}") String url,
 *             @Value("${spring.datasource.username}") String username,
 *             @Value("${spring.datasource.password}") String password) {
 *         HikariConfig config = new HikariConfig();
 *         config.setJdbcUrl(url);
 *         config.setUsername(username);
 *         config.setPassword(password);
 *         config.setMaximumPoolSize(10);
 *         return new TenantAwareDataSource(new HikariDataSource(config));
 *     }
 * }
 * }</pre>
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    public TenantAwareDataSource(DataSource targetDataSource) {
        super(targetDataSource);
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = super.getConnection();
        applyTenantContext(connection);
        return connection;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection connection = super.getConnection(username, password);
        applyTenantContext(connection);
        return connection;
    }

    /**
     * Executes {@code SET LOCAL app.current_tenant_id = ?} on the connection
     * if a tenant ID is present in {@link TenantContext}.
     *
     * <p>Uses a {@link PreparedStatement} (not string concatenation) to prevent
     * SQL injection via a crafted tenant ID header.
     *
     * @param connection the connection just checked out from HikariCP
     * @throws SQLException if the session variable cannot be set
     */
    private void applyTenantContext(Connection connection) throws SQLException {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId != null && !tenantId.isBlank()) {
            // PostgreSQL does not support parameter binding (?) in the "SET" statement.
            // Instead, we use "SELECT set_config('app.current_tenant_id', ?, true)"
            // where the third parameter 'true' makes it local to the transaction.
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT set_config('app.current_tenant_id', ?, true)")) {
                ps.setString(1, tenantId);
                try (var rs = ps.executeQuery()) {
                    // Execute query to apply config
                }
            }
        }
    }
}
