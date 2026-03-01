package com.bugisiw.marketplace.common.model.security;

import lombok.Data;

/**
 * Request model for user login.
 */
@Data
public class LoginRequest {
    private String username;
    private String password;
} 