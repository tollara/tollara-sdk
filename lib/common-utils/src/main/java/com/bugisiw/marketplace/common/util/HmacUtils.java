package com.bugisiw.marketplace.common.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for HMAC signature generation and validation
 */
@Slf4j
public class HmacUtils {

    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";
    private static final ObjectMapper objectMapper = createObjectMapper();
    
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return mapper;
    }

    /**
     * Validates an HMAC signature against a payload object using a provided secret key
     *
     * @param signature The signature to validate
     * @param payload The payload object that was signed
     * @param secretKey The secret key used for signing
     * @return true if the signature is valid, false otherwise
     */
    public static boolean validateHmacSignature(String signature, Object payload, String secretKey) {
        if (signature == null || signature.isEmpty() || secretKey == null || secretKey.isEmpty()) {
            return false;
        }

        try {
            String payloadString = objectMapper.writeValueAsString(payload);
            return validateHmacSignature(signature, payloadString, secretKey);
        } catch (Exception e) {
            log.error("Error validating HMAC signature for object: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Validates an HMAC signature against a payload string using a provided secret key
     *
     * @param signature The signature to validate
     * @param payloadString The payload string that was signed
     * @param secretKey The secret key used for signing
     * @return true if the signature is valid, false otherwise
     */
    public static boolean validateHmacSignature(String signature, String payloadString, String secretKey) {
        if (signature == null || signature.isEmpty() || secretKey == null || secretKey.isEmpty()) {
            return false;
        }

        try {
            String expectedSignature = calculateHmac(payloadString, secretKey);
            return expectedSignature.equals(signature);
        } catch (Exception e) {
            log.error("Error validating HMAC signature: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Calculates an HMAC-SHA256 signature for the given data using the provided key
     *
     * @param data The data to be signed
     * @param key The key to use for signing
     * @return The Base64-encoded HMAC signature
     * @throws Exception If an error occurs during signature calculation
     */
    public static String calculateHmac(String data, String key) throws Exception {
        Mac sha256Hmac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA256_ALGORITHM);
        sha256Hmac.init(secretKey);
        byte[] hmacBytes = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hmacBytes);
    }
    
    /**
     * Calculates an HMAC-SHA256 signature for the given object using the provided key
     *
     * @param object The object to be signed
     * @param key The key to use for signing
     * @return The Base64-encoded HMAC signature
     * @throws Exception If an error occurs during signature calculation
     */
    public static String calculateHmac(Object object, String key) throws Exception {
        String data = objectMapper.writeValueAsString(object);
        return calculateHmac(data, key);
    }
    
    /**
     * Calculates an HMAC-SHA256 signature for the given data with timestamp using the provided key.
     * The timestamp is concatenated with the data before signing.
     *
     * @param data The data to be signed
     * @param timestamp The timestamp to include in the signature
     * @param key The key to use for signing
     * @return The Base64-encoded HMAC signature
     */
    public static String calculateHmacWithTimestamp(String data, long timestamp, String key) {
        if (key == null || key.isEmpty()) {
            log.warn("Key is null or empty - cannot generate secure signature");
            return "";
        }
        
        try {
            // Concatenate payload and timestamp as data to sign
            String dataWithTimestamp = data + timestamp;
            log.debug(">>>>> [HMAC] Calculating signature - data: '{}', timestamp: {}, dataWithTimestamp: '{}', key: '{}'", 
                    data, timestamp, dataWithTimestamp, key);
            String signature = calculateHmac(dataWithTimestamp, key);
            log.debug(">>>>> [HMAC] Calculated signature: {}", signature);
            return signature;
        } catch (Exception e) {
            log.error("Failed to generate HMAC signature with timestamp: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate signature", e);
        }
    }
    
    /**
     * Calculates an HMAC-SHA256 signature for the given object with timestamp using the provided key.
     * The object is serialized to JSON, and the timestamp is concatenated before signing.
     *
     * @param object The object to be signed
     * @param timestamp The timestamp to include in the signature
     * @param key The key to use for signing
     * @return The Base64-encoded HMAC signature
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
} 