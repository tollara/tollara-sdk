package com.bugisiw.marketplace.common.model.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OAuth2-style response from security-service POST /auth/service-token.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceTokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("expires_in")
    private int expiresIn;

    @JsonProperty("token_type")
    private String tokenType;
}
