package com.bugisiw.marketplace.common.model.security;

import lombok.Data;

/**
 * Request model for confirming user registration.
 */
@Data
public class ConfirmRegistrationRequest {
    private String username;
    private String confirmationCode;
} 