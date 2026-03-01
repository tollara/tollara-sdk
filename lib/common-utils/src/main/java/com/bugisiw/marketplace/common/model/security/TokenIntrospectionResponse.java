package com.bugisiw.marketplace.common.model.security;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response model for token introspection endpoint.
 * Contains information about the token's validity and associated user details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenIntrospectionResponse {
    
    /**
     * Whether the token is active and valid.
     */
    private boolean active;
    
    /**
     * The user ID associated with the token.
     */
    private String user_id;
    
    /**
     * The roles/groups assigned to the user.
     */
    private List<String> roles;
    
    /**
     * The user's subscription tier.
     */
    private String subscription_tier;
    
    /**
     * The expiration time of the token.
     */
    private Instant expires_at;
} 