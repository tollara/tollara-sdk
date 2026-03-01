package com.bugisiw.marketplace.common.model.security;

import lombok.Data;

/**
 * Request model for forgot password.
 */
@Data
public class ForgotPasswordRequest {
    private String username;
}

