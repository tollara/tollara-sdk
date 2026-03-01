package com.bugisiw.marketplace.common.model.agent;

/**
 * Enum representing the different types of agents supported by the marketplace.
 */
public enum AgentType {
    API,           // Standard API endpoint agents
    MCP_SERVER,    // MCP (Model Control Protocol) server agents
    NO_CODE,       // No-code workflow agents (e.g., n8n, Gumloop)
    WEB_BUTTON     // Web button/widget agents
} 