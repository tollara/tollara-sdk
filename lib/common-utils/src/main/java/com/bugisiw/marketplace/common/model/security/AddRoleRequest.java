package com.bugisiw.marketplace.common.model.security;

import lombok.Data;

/**
 * Request model for adding a role to the current authenticated user.
 */
@Data
public class AddRoleRequest {
    private String role;
}

