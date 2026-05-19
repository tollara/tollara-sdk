package com.tollara.common.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for HMAC signature generation and validation (vendored for SDK).
 */
@Slf4j
public class HmacUtils {

    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";
    private static final ObjectMapper objectMapper = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return mapper;
    }

    /**
     * Validates an HMAC signature against a payload string using a provided secret key.
     */
    public static boolean validateHmacSignature(String signature, String payloadString, String secretKey) {
        if (signature == null || signature.isEmpty() || secretKey == null || secretKey.isEmpty()) {
            return false;
        }
        try {
            String expectedSignature = calculateHmac(payloadString, secretKey);
            return constantTimeEquals(expectedSignature, signature);
        } catch (Exception e) {
            log.debug("Error validating HMAC signature: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Calculates an HMAC-SHA256 signature for the given data using the provided key.
     * Key and data are UTF-8; output is Base64-encoded.
     */
    public static String calculateHmac(String data, String key) throws Exception {
        Mac sha256Hmac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA256_ALGORITHM);
        sha256Hmac.init(secretKey);
        byte[] hmacBytes = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hmacBytes);
    }

    /**
     * Calculates an HMAC-SHA256 signature for the given data with timestamp.
     * Canonical string = data + timestamp (concatenation).
     */
    public static String calculateHmacWithTimestamp(String data, long timestamp, String key) {
        if (key == null || key.isEmpty()) {
            log.warn("Key is null or empty - cannot generate secure signature");
            return "";
        }
        try {
            String dataWithTimestamp = data + timestamp;
            return calculateHmac(dataWithTimestamp, key);
        } catch (Exception e) {
            log.error("Failed to generate HMAC signature with timestamp: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate signature", e);
        }
    }

    /**
     * Calculates an HMAC-SHA256 signature for the given object with timestamp.
     * Object is serialized to JSON, then data + timestamp is signed.
     */
    public static String calculateHmacWithTimestamp(Object object, long timestamp, String key) {
        try {
            String data = objectMapper.writeValueAsString(object);
            return calculateHmacWithTimestamp(data, timestamp, key);
        } catch (Exception e) {
            log.error("Failed to generate HMAC signature for object with timestamp: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate signature", e);
        }
    }

    /** Constant-time string comparison to avoid timing attacks. */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
