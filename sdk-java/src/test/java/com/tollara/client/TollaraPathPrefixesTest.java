package com.tollara.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TollaraPathPrefixesTest {

    @Test
    void detectsHostedTollaraApiOrigin() {
        assertTrue(TollaraPathPrefixes.isHostedTollaraApiOrigin("https://api.tollara.ai"));
        assertTrue(TollaraPathPrefixes.isHostedTollaraApiOrigin("https://acme.api.tollara.ai"));
        assertFalse(TollaraPathPrefixes.isHostedTollaraApiOrigin("http://host.docker.internal:8083"));
    }

    @Test
    void resolvesEcsGatewayPrefixForProd() {
        assertEquals(
                TollaraPathPrefixes.ECS_GATEWAY_PATH_PREFIX,
                TollaraPathPrefixes.resolveGatewayPathPrefix("https://api.tollara.ai", null));
        assertEquals(
                TollaraClient.DEFAULT_GATEWAY_PATH_PREFIX,
                TollaraPathPrefixes.resolveGatewayPathPrefix("http://host.docker.internal:8083", null));
    }

    @Test
    void honoursExplicitOverride() {
        assertEquals("/api", TollaraPathPrefixes.resolveGatewayPathPrefix("https://api.tollara.ai", "/api"));
    }
}
