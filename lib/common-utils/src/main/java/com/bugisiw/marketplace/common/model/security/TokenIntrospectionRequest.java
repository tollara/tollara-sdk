package com.bugisiw.marketplace.common.model.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request model for token introspection endpoint.
 * Contains the token to be validated.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenIntrospectionRequest {
    
    /**
     * The JWT token to be validated.
     */
    private String token;
} 