package com.bugisiw.marketplace.common.model.security;

import lombok.Data;
import java.util.Map;

/**
 * Request model for user registration.
 * For AGENT_OWNER userType, business profile fields can be provided to create the agent owner record during registration.
 */
@Data
public class RegisterRequest {
    private String username;
    private String email;
    private String password;
    private String name;
    private String userType; // USER or AGENT_OWNER
    private Map<String, String> customAttributes;
    
    // Business profile fields (optional, used when userType is AGENT_OWNER)
    private String businessType; // INDIVIDUAL or COMPANY
    private String businessName; // Required if businessType is COMPANY
    private String businessProfileUrl; // Optional
} 