package com.maple.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * JPA entity representing an audit event in the system.
 * 
 * This is an append-only table that captures every significant action
 * in the payment system for compliance and forensic analysis.
 */
@Entity
@Table(name = "audit_events", 
       indexes = {
           @Index(name = "idx_audit_events_target", columnList = "targetType, targetId"),
           @Index(name = "idx_audit_events_actor", columnList = "actorType, actorId"),
           @Index(name = "idx_audit_events_action", columnList = "action"),
           @Index(name = "idx_audit_events_created_at", columnList = "createdAt")
       })
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false)
    @NotNull
    private ActorType actorType;

    @Column(name = "actor_id")
    @Size(max = 255)
    private String actorId;

    @Column(name = "action", nullable = false, length = 100)
    @NotBlank
    @Size(max = 100)
    private String action;

    @Column(name = "target_type", nullable = false, length = 100)
    @NotBlank
    @Size(max = 100)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    @NotBlank
    @Size(max = 255)
    private String targetId;

    @Column(name = "details", columnDefinition = "jsonb")
    private String details;

    @Column(name = "request_id", length = 128)
    @Size(max = 128)
    private String requestId;

    @Column(name = "session_id", length = 128)
    @Size(max = 128)
    private String sessionId;

    @Column(name = "ip_address")
    private InetAddress ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // Constructors
    public AuditEvent() {}

    public AuditEvent(ActorType actorType, String actorId, String action, 
                     String targetType, String targetId) {
        this.actorType = actorType;
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
    }

    public AuditEvent(ActorType actorType, String actorId, String action, 
                     String targetType, String targetId, String details) {
        this(actorType, actorId, action, targetType, targetId);
        this.details = details;
    }

    // Static factory methods for common audit events
    
    public static AuditEvent userAction(String userId, String action, 
                                      String targetType, String targetId) {
        return new AuditEvent(ActorType.USER, userId, action, targetType, targetId);
    }

    public static AuditEvent systemAction(String action, String targetType, String targetId) {
        return new AuditEvent(ActorType.SYSTEM, "SYSTEM", action, targetType, targetId);
    }

    public static AuditEvent externalPartnerAction(String partnerId, String action, 
                                                 String targetType, String targetId) {
        return new AuditEvent(ActorType.EXTERNAL_PARTNER, partnerId, action, targetType, targetId);
    }

    // Business methods
    
    /**
     * Enriches the audit event with request context information.
     */
    public AuditEvent withRequestContext(String requestId, String sessionId, 
                                       InetAddress ipAddress, String userAgent) {
        this.requestId = requestId;
        this.sessionId = sessionId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        return this;
    }

    /**
     * Adds detailed information about the action in JSON format.
     */
    public AuditEvent withDetails(String details) {
        this.details = details;
        return this;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ActorType getActorType() { return actorType; }
    public void setActorType(ActorType actorType) { this.actorType = actorType; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public InetAddress getIpAddress() { return ipAddress; }
    public void setIpAddress(InetAddress ipAddress) { this.ipAddress = ipAddress; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditEvent that = (AuditEvent) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "AuditEvent{" +
                "id=" + id +
                ", actorType=" + actorType +
                ", actorId='" + actorId + '\'' +
                ", action='" + action + '\'' +
                ", targetType='" + targetType + '\'' +
                ", targetId='" + targetId + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }

    /**
     * Enumeration for types of actors that can perform actions in the system.
     */
    public enum ActorType {
        USER,           // Human user
        SYSTEM,         // Automated system process
        EXTERNAL_PARTNER // External partner system
    }
}
