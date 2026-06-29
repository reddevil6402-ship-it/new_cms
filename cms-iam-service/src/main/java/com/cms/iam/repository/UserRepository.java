package com.cms.iam.repository;

import com.cms.iam.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("SELECT u FROM User u JOIN FETCH u.roles r JOIN FETCH r.permissions " +
           "WHERE u.email = :email AND u.tenant.code = :tenantCode")
    Optional<User> findByEmailAndTenantCodeWithRoles(
            @Param("email") String email,
            @Param("tenantCode") String tenantCode);

    Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByEmailAndTenantId(String email, UUID tenantId);
}
