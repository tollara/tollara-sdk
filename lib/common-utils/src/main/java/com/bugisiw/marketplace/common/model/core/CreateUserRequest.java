package com.bugisiw.marketplace.common.model.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request model for creating a user in the core-service database.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {
    private String extUserId;
    private String email;
    private String userType;
    private String name;
    private Boolean userConfirmed; // Whether the user's email is confirmed in Cognito
}

