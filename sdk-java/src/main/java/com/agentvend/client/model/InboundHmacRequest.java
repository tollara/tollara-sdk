package com.agentvend.client.model;

import lombok.Builder;
import lombok.Value;

/**
 * Inbound gateway request material needed to verify {@code X-AgentVend-Signature}.
 * Canonical string: {@code payload + timestamp + userContextString}.
 */
@Value
@Builder
public class InboundHmacRequest {
    String signature;
    String timestamp;
    /** Raw body string or object serialized with the verifier's ObjectMapper. */
    Object payload;
    SignedUserContext signedUserContext;
    /**
     * {@link AgentVendHeaders#SIGNING_VERSION}: when {@code "2"}, {@link com.agentvend.common.util.GatewayHmacUserContext#buildV2} is used for signing.
     */
    String signingVersion;
}
