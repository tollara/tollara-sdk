package com.bugisiw.marketplace.common.model.security;

import lombok.Builder;
import lombok.Getter;

/**
 * Response model for user registration.
 */
@Getter
@Builder
public class RegisterResponse {
    private String username;
    private boolean userConfirmed;
    private String userSub;
} 