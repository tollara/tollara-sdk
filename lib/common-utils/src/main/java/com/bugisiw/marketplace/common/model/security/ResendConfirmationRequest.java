package com.bugisiw.marketplace.common.model.security;

import lombok.Data;

/**
 * Request model for resending confirmation code.
 */
@Data
public class ResendConfirmationRequest {
    private String username;
}
