package com.bugisiw.marketplace.common.exception;

/**
 * Exception thrown when an agent cannot be found by its ID.
 * This is an unchecked exception (extends RuntimeException).
 */
public class AgentNotFoundException extends RuntimeException {
    
    private final String agentId;
    
    /**
     * Constructs a new AgentNotFoundException with the specified agent ID.
     *
     * @param agentId the ID of the agent that was not found
     */
    public AgentNotFoundException(String agentId) {
        super("Agent not found with ID: " + agentId);
        this.agentId = agentId;
    }
    
    /**
     * Returns the ID of the agent that was not found.
     *
     * @return the agent ID
     */
    public String getAgentId() {
        return agentId;
    }
} 