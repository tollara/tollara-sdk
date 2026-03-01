package com.bugisiw.marketplace.common.model.security;

import lombok.Data;

/**
 * Request model for adding a user to a group.
 */
@Data
public class AddUserToGroupRequest {
    private String username;
    private String groupName;
} 