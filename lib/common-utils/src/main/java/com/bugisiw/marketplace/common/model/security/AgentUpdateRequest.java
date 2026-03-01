package com.bugisiw.marketplace.common.model.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request object for agent updates.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentUpdateRequest {
    private String name;
    private String description;
    private String apiEndpoint;
    private AgentRegistrationRequest.PricingModel pricingModel;
    private List<String> supportedMethods;
} 