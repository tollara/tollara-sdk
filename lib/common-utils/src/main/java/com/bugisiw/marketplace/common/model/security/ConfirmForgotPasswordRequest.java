package com.bugisiw.marketplace.common.model.security;

import lombok.Data;

/**
 * Request model for confirming forgot password with verification code.
 */
@Data
public class ConfirmForgotPasswordRequest {
    private String username;
    private String confirmationCode;
    private String newPassword;
}

