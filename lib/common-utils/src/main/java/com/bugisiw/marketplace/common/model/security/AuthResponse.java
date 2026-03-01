package com.bugisiw.marketplace.common.model.security;

import lombok.Builder;
import lombok.Getter;

/**
 * Response model for authentication containing tokens.
 */
@Getter
@Builder
public class AuthResponse {
    private String accessToken;
    private String idToken;
    private String refreshToken;
    private int expiresIn;
    private String tokenType;
} 