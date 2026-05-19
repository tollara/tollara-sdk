package com.tollara.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HMAC utilities; aligns with docs/hmac-spec.md test vectors.
 */
class HmacUtilsTest {

    @Test
    void calculateHmac_outboundTestVector() throws Exception {
        String data = "1234567890";
        String key = "secret";
        String signature = HmacUtils.calculateHmac(data, key);
        assertEquals("Bgs+chJF8gBA3xW2542Tm7B7l571zTPfLMBiCBwOp2c=", signature);
    }

    @Test
    void calculateHmacWithTimestamp() {
        String data = "{}";
        long timestamp = 1700000000L;
        String key = "k";
        String sig = HmacUtils.calculateHmacWithTimestamp(data, timestamp, key);
        assertNotNull(sig);
        assertFalse(sig.isEmpty());
    }

    @Test
    void validateHmacSignature_valid() throws Exception {
        String data = "1234567890";
        String key = "secret";
        String signature = HmacUtils.calculateHmac(data, key);
        assertTrue(HmacUtils.validateHmacSignature(signature, data, key));
    }

    @Test
    void validateHmacSignature_invalid() {
        assertFalse(HmacUtils.validateHmacSignature("wrong", "1234567890", "secret"));
    }

    @Test
    void constantTimeEquals() {
        assertTrue(HmacUtils.constantTimeEquals("a", "a"));
        assertFalse(HmacUtils.constantTimeEquals("a", "b"));
        assertFalse(HmacUtils.constantTimeEquals("ab", "a"));
    }
}
