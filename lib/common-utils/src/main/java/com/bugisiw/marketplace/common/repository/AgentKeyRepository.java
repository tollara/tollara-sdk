package com.bugisiw.marketplace.common.repository;

import com.bugisiw.marketplace.common.model.agent.AgentKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for AgentKey entities.
 */
@Repository
public interface AgentKeyRepository extends JpaRepository<AgentKey, UUID> {

    /**
     * Finds an active agent key by its hash.
     *
     * @param keyHash The hashed key value
     * @return Optional containing the agent key if found and active
     */
    Optional<AgentKey> findByKeyHashAndRevokedAtIsNull(String keyHash);

    /**
     * Finds all active agent keys for a user.
     *
     * @param extUserId The external user ID
     * @return List of active agent keys for the user
     */
    List<AgentKey> findByExtUserIdAndRevokedAtIsNull(String extUserId);

    /**
     * Finds all active agent keys for a user and agent.
     *
     * @param extUserId The external user ID
     * @param agentId The agent ID
     * @return List of active agent keys for the user and agent
     */
    List<AgentKey> findByExtUserIdAndAgentIdAndRevokedAtIsNull(String extUserId, UUID agentId);

    /**
     * Finds an agent key by ID and user ID.
     *
     * @param id The agent key ID
     * @param extUserId The external user ID
     * @return Optional containing the agent key if found
     */
    Optional<AgentKey> findByIdAndExtUserId(UUID id, String extUserId);

    /**
     * Finds an active agent key by hash and agent ID.
     *
     * @param keyHash The hashed key value
     * @param agentId The agent ID
     * @return Optional containing the agent key if found and active
     */
    Optional<AgentKey> findByKeyHashAndAgentIdAndRevokedAtIsNull(String keyHash, UUID agentId);

    /**
     * Finds all active agent keys for an agent.
     *
     * @param agentId The agent ID
     * @return List of active agent keys for the agent
     */
    List<AgentKey> findByAgentIdAndRevokedAtIsNull(UUID agentId);

    /**
     * Finds an active agent key by its lookup hash (SHA-256 of plain key).
     * Used for fast lookup when agentId is unknown; avoids loading all keys and N BCrypt checks.
     *
     * @param keyLookupHash SHA-256 hex of the plain agent key
     * @return Optional containing the agent key if found and active
     */
    Optional<AgentKey> findByKeyLookupHashAndRevokedAtIsNull(String keyLookupHash);

    /**
     * Finds active agent keys that have no key_lookup_hash (legacy keys created before V36).
     * Used only as fallback in findAgentIdByKey to avoid loading all keys via findAll().
     *
     * @return List of active legacy keys (small set; BCrypt check done in service)
     */
    List<AgentKey> findByRevokedAtIsNullAndKeyLookupHashIsNull();
}

