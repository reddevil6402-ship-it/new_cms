package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.PreparedStatement;

/**
 * V4: Fix the SUPER_ADMIN password hash.
 *
 * The V3 SQL migration seeded a fabricated bcrypt hash that does not match
 * "Admin@123!". This Java migration generates the correct hash at runtime
 * using BCryptPasswordEncoder (cost 12 — matching the locked decision in
 * auth-flow.md) and updates the seeded user.
 *
 * Local dev credentials after this migration:
 *   email:      admin@cms.system
 *   password:   Admin@123!
 *   tenantCode: cms-system
 */
public class V4__Fix_super_admin_password_hash extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        // BCryptPasswordEncoder is on the classpath via spring-boot-starter-security
        String correctHash = new BCryptPasswordEncoder(12).encode("Admin@123!");

        try (PreparedStatement ps = context.getConnection().prepareStatement(
                "UPDATE iam.users SET password_hash = ? WHERE email = 'admin@cms.system'")) {
            ps.setString(1, correctHash);
            int updated = ps.executeUpdate();
            if (updated != 1) {
                throw new IllegalStateException(
                    "V4 migration expected to update 1 row but updated " + updated +
                    " — check that V3 super_admin seed ran correctly.");
            }
        }
    }
}
