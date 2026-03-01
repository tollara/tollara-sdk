package com.bugisiw.marketplace.common.model.security;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Response model for user information.
 */
@Getter
@Builder
public class UserInfoResponse {
    private String sub;
    private String email;
    private String name;
    private String username;
    private List<String> cognitoGroups;
    private Map<String, String> attributes;
} 