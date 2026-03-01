package com.bugisiw.marketplace.common.model.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Request object for agent registration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentRegistrationRequest {
    private String name;
    private String description;
    private String apiEndpoint;
    private String ownerEmail;
    private PricingModel pricingModel;
    private List<String> supportedMethods = new ArrayList<>(List.of("POST")); // Default to POST for backward compatibility
    
    /**
     * Pricing model for the agent.
     */
    public enum PricingModel {
        FREE,
        SUBSCRIPTION,
        PAY_PER_USE
    }
} 