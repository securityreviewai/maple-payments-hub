package com.maple.repository;

import com.maple.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for User entity operations.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find user by username.
     */
    Optional<User> findByUsername(String username);

    /**
     * Find user by email.
     */
    Optional<User> findByEmail(String email);

    /**
     * Find active users.
     */
    List<User> findByIsActiveTrue();

    /**
     * Find users by role (array contains).
     */
    @Query("SELECT u FROM User u WHERE :role = ANY(u.roles)")
    List<User> findByRole(@Param("role") String role);

    /**
     * Find users with approval privileges.
     */
    @Query("SELECT u FROM User u WHERE ('ROLE_TREASURY_MANAGER' = ANY(u.roles) OR 'ROLE_CLEARING' = ANY(u.roles)) AND u.isActive = true")
    List<User> findApprovers();

    /**
     * Check if username exists.
     */
    boolean existsByUsername(String username);

    /**
     * Check if email exists.
     */
    boolean existsByEmail(String email);
}
