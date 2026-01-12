package com.maple.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity representing a user in the system.
 * 
 * Users have roles that determine their access permissions and approval limits
 * for payment operations.
 */
@Entity
@Table(name = "users", 
       indexes = {
           @Index(name = "idx_users_username", columnList = "username"),
           @Index(name = "idx_users_email", columnList = "email")
       })
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "username", nullable = false, unique = true, length = 100)
    @NotBlank
    @Size(max = 100)
    private String username;

    @Column(name = "email", nullable = false, unique = true)
    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @Column(name = "full_name", nullable = false)
    @NotBlank
    @Size(max = 255)
    private String fullName;

    @Column(name = "roles", columnDefinition = "text[]")
    private String[] roles = new String[0];

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "requires_approval", nullable = false)
    private Boolean requiresApproval = false;

    @Column(name = "approval_limit_cents")
    private Long approvalLimitCents = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // Constructors
    public User() {}

    public User(String username, String email, String fullName) {
        this.username = username;
        this.email = email;
        this.fullName = fullName;
    }

    public User(String username, String email, String fullName, String... roles) {
        this(username, email, fullName);
        this.roles = roles;
    }

    // Business methods
    
    /**
     * @return list of roles assigned to this user
     */
    public List<String> getRolesList() {
        return roles != null ? Arrays.asList(roles) : new ArrayList<>();
    }

    /**
     * Adds a role to the user if not already present.
     */
    public void addRole(String role) {
        List<String> currentRoles = new ArrayList<>(getRolesList());
        if (!currentRoles.contains(role)) {
            currentRoles.add(role);
            this.roles = currentRoles.toArray(new String[0]);
        }
    }

    /**
     * Removes a role from the user.
     */
    public void removeRole(String role) {
        List<String> currentRoles = new ArrayList<>(getRolesList());
        currentRoles.remove(role);
        this.roles = currentRoles.toArray(new String[0]);
    }

    /**
     * @param role the role to check
     * @return true if user has the specified role
     */
    public boolean hasRole(String role) {
        return getRolesList().contains(role);
    }

    /**
     * @return true if user can approve payments (has manager or clearing role)
     */
    public boolean canApprovePayments() {
        return hasRole("ROLE_TREASURY_MANAGER") || hasRole("ROLE_CLEARING");
    }

    /**
     * @return true if user can submit payments to clearing
     */
    public boolean canSubmitToClearing() {
        return hasRole("ROLE_CLEARING");
    }

    /**
     * @return true if user can access audit information
     */
    public boolean canAccessAudit() {
        return hasRole("ROLE_AUDITOR") || hasRole("ROLE_TREASURY_MANAGER");
    }

    /**
     * @param amountCents the payment amount to check
     * @return true if this payment amount requires approval for this user
     */
    public boolean requiresApprovalForAmount(long amountCents) {
        return requiresApproval || amountCents > approvalLimitCents;
    }

    /**
     * Deactivates the user account.
     */
    public void deactivate() {
        this.isActive = false;
    }

    /**
     * Activates the user account.
     */
    public void activate() {
        this.isActive = true;
    }

    /**
     * Updates the approval limit for this user.
     * 
     * @param newLimitCents New approval limit in cents
     */
    public void updateApprovalLimit(long newLimitCents) {
        if (newLimitCents < 0) {
            throw new IllegalArgumentException("Approval limit cannot be negative");
        }
        this.approvalLimitCents = newLimitCents;
    }

    /**
     * @return true if user has any administrative roles
     */
    public boolean isAdministrator() {
        return hasRole("ROLE_ADMIN") || hasRole("ROLE_TREASURY_MANAGER");
    }

    /**
     * @return true if user has read-only access (auditor)
     */
    public boolean isReadOnly() {
        return hasRole("ROLE_AUDITOR") && getRolesList().size() == 1;
    }

    /**
     * @return formatted approval limit for display
     */
    public String getFormattedApprovalLimit() {
        if (approvalLimitCents == null || approvalLimitCents == 0) {
            return "No limit";
        }
        return String.format("$%,.2f", approvalLimitCents / 100.0);
    }

    /**
     * Checks if user can perform operations on payments.
     * 
     * @return true if user can perform payment operations
     */
    public boolean canPerformPaymentOperations() {
        return isActive && (hasRole("ROLE_TREASURY_OPS") || 
                           hasRole("ROLE_TREASURY_MANAGER") || 
                           hasRole("ROLE_CLEARING"));
    }

    /**
     * Gets a display name for the user (full name or username).
     * 
     * @return Display name
     */
    public String getDisplayName() {
        return fullName != null && !fullName.trim().isEmpty() ? fullName : username;
    }

    /**
     * Validates that the user account is in a valid state.
     * 
     * @return true if user account is valid
     */
    public boolean isValid() {
        return username != null && !username.trim().isEmpty() &&
               email != null && !email.trim().isEmpty() &&
               fullName != null && !fullName.trim().isEmpty();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String[] getRoles() { return roles; }
    public void setRoles(String[] roles) { this.roles = roles; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Boolean getRequiresApproval() { return requiresApproval; }
    public void setRequiresApproval(Boolean requiresApproval) { this.requiresApproval = requiresApproval; }

    public Long getApprovalLimitCents() { return approvalLimitCents; }
    public void setApprovalLimitCents(Long approvalLimitCents) { this.approvalLimitCents = approvalLimitCents; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id) && Objects.equals(username, user.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, username);
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", fullName='" + fullName + '\'' +
                ", isActive=" + isActive +
                '}';
    }
}
