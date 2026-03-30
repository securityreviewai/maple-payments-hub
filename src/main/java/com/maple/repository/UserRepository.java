package com.maple.repository;

import com.maple.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
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

    /**
     * Find users by multiple roles (any of the roles).
     */
    @Query("SELECT DISTINCT u FROM User u WHERE :role1 = ANY(u.roles) OR :role2 = ANY(u.roles)")
    List<User> findByAnyRole(@Param("role1") String role1, @Param("role2") String role2);

    /**
     * Find active users with approval limits above threshold.
     */
    @Query("SELECT u FROM User u WHERE u.isActive = true AND u.approvalLimitCents >= :thresholdCents")
    List<User> findActiveUsersWithApprovalLimitAbove(@Param("thresholdCents") Long thresholdCents);

    /**
     * Find users who require approval for any amount.
     */
    @Query("SELECT u FROM User u WHERE u.requiresApproval = true AND u.isActive = true")
    List<User> findUsersRequiringApproval();

    /**
     * Count active users by role.
     */
    @Query("SELECT COUNT(DISTINCT u) FROM User u WHERE :role = ANY(u.roles) AND u.isActive = true")
    Long countActiveUsersByRole(@Param("role") String role);

    /**
     * Find users by username or email (case-insensitive search).
     */
    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<User> searchUsers(@Param("searchTerm") String searchTerm, Pageable pageable);
}
