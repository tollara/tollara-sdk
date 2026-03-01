package com.bugisiw.marketplace.common.model.agent;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing an Agent Key (agent-scoped API key).
 * Each key is tied to a specific agent and user.
 */
@Entity
@Table(name = "agent_keys")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentKey implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "ext_user_id", nullable = false)
    private String extUserId;

    @Column(name = "agent_id", nullable = false, columnDefinition = "UUID")
    private UUID agentId;

    @Column(name = "key_hash", nullable = false, unique = true, length = 255)
    private String keyHash;

    /**
     * SHA-256 hex of the plain key for fast lookup when agentId is unknown.
     * Set at creation only; used by findAgentIdByKey to avoid findAll() + N BCrypt checks.
     */
    @Column(name = "key_lookup_hash", length = 64)
    private String keyLookupHash;

    @Column(name = "key_prefix", nullable = false, length = 20)
    private String keyPrefix;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Checks if the key is active (not revoked).
     *
     * @return true if the key is active, false otherwise
     */
    public boolean isActive() {
        return revokedAt == null;
    }
}

