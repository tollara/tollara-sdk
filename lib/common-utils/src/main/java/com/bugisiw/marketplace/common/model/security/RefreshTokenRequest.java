package com.bugisiw.marketplace.common.model.security;

import lombok.Data;

/**
 * Request model for refreshing tokens.
 */
@Data
public class RefreshTokenRequest {
    
    private String refreshToken;
    
    // Optional username field that can be used for SECRET_HASH calculation
    private String username;
} 