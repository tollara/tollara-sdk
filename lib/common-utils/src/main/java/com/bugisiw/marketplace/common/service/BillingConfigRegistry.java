package com.bugisiw.marketplace.common.service;

import com.bugisiw.marketplace.common.model.billing.BillingModelType;
import com.bugisiw.marketplace.common.model.billing.config.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry mapping BillingModelType to config table names, DTO classes, and JSON schemas.
 * Used by services to dynamically load correct config and generate frontend forms.
 */
@Service
public class BillingConfigRegistry {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private static final Map<BillingModelType, ConfigMetadata> CONFIG_METADATA = new HashMap<>();

    static {
        // SUBSCRIPTION model
        CONFIG_METADATA.put(BillingModelType.SUBSCRIPTION, ConfigMetadata.builder()
                .configTableName("agent_product_subscription_config")
                .configClass(AgentProductSubscriptionConfig.class)
                .configSchema(createSubscriptionSchema())
                .build());

        // USAGE_POSTPAID model
        CONFIG_METADATA.put(BillingModelType.USAGE_POSTPAID, ConfigMetadata.builder()
                .configTableName("agent_product_per_request_config")
                .configClass(AgentProductPerRequestConfig.class)
                .configSchema(createPerRequestSchema())
                .build());

        // USAGE_INSTANT model
        CONFIG_METADATA.put(BillingModelType.USAGE_INSTANT, ConfigMetadata.builder()
                .configTableName("agent_product_per_request_config")
                .configClass(AgentProductPerRequestConfig.class)
                .configSchema(createPerRequestSchema())
                .build());

        // PREPAID model
        CONFIG_METADATA.put(BillingModelType.PREPAID, ConfigMetadata.builder()
                .configTableName("agent_product_prepaid_config")
                .configClass(AgentProductPrepaidConfig.class)
                .configSchema(createPrepaidSchema())
                .build());
    }

    /**
     * Get the config table name for a billing model type.
     */
    public String getConfigTableName(BillingModelType modelType) {
        ConfigMetadata metadata = CONFIG_METADATA.get(modelType);
        return metadata != null ? metadata.configTableName : null;
    }

    /**
     * Get the config class for a billing model type.
     */
    public Class<?> getConfigClass(BillingModelType modelType) {
        ConfigMetadata metadata = CONFIG_METADATA.get(modelType);
        return metadata != null ? metadata.configClass : null;
    }

    /**
     * Get the JSON schema for a billing model type (for frontend form generation).
     */
    public JsonNode getConfigSchema(BillingModelType modelType) {
        ConfigMetadata metadata = CONFIG_METADATA.get(modelType);
        return metadata != null ? metadata.configSchema : null;
    }

    /**
     * Get the JSON schema for tiered pricing config (used for USAGE_POSTPAID with tiered billing scheme).
     */
    public JsonNode getTieredConfigSchema() {
        return createTieredSchema();
    }

    /**
     * Get all billing model types with their metadata.
     */
    public Map<BillingModelType, ConfigMetadata> getAllMetadata() {
        return new HashMap<>(CONFIG_METADATA);
    }

    private static JsonNode createSubscriptionSchema() {
        try {
            String schemaJson = """
                {
                    "type": "object",
                    "properties": {
                        "interval": {
                            "type": "string",
                            "enum": ["day", "week", "month", "year"]
                        },
                        "intervalCount": {
                            "type": "integer",
                            "default": 1
                        },
                        "trialDays": {
                            "type": "integer"
                        },
                        "includedUnits": {
                            "type": "number"
                        },
                        "overageRate": {
                            "type": "number"
                        },
                        "unitLabel": {
                            "type": "string",
                            "default": "request"
                        },
                        "measurementType": {
                            "type": "string",
                            "enum": ["PER_REQUEST", "PER_TIME_UNIT", "PER_TOKEN", "PER_BYTE"]
                        },
                        "tokenizerEncoding": {
                            "type": "string",
                            "description": "JTokkit encoding name for PER_TOKEN (e.g. cl100k_base)"
                        }
                    },
                    "required": ["interval"]
                }
                """;
            return objectMapper.readTree(schemaJson);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create subscription schema", e);
        }
    }

    private static JsonNode createPerRequestSchema() {
        try {
            String schemaJson = """
                {
                    "type": "object",
                    "properties": {
                        "unitLabel": {
                            "type": "string",
                            "default": "request"
                        },
                        "perUnitPrice": {
                            "type": "number"
                        },
                        "billingPeriod": {
                            "type": "string",
                            "enum": ["none", "day", "week", "month", "year"]
                        },
                        "baseUnits": {
                            "type": "number"
                        },
                        "maxUnits": {
                            "type": "number"
                        },
                        "trialDays": {
                            "type": "integer",
                            "minimum": 0
                        },
                        "measurementType": {
                            "type": "string",
                            "enum": ["PER_REQUEST", "PER_TIME_UNIT", "PER_TOKEN", "PER_BYTE"]
                        },
                        "tokenizerEncoding": {
                            "type": "string",
                            "description": "JTokkit encoding name for PER_TOKEN (e.g. cl100k_base)"
                        }
                    },
                    "required": ["perUnitPrice"]
                }
                """;
            return objectMapper.readTree(schemaJson);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create per-request schema", e);
        }
    }

    private static JsonNode createPrepaidSchema() {
        try {
            String schemaJson = """
                {
                    "type": "object",
                    "properties": {
                        "unitLabel": {
                            "type": "string",
                            "default": "token"
                        },
                        "packUnits": {
                            "type": "number"
                        },
                        "expiresAfterDays": {
                            "type": "integer"
                        }
                    },
                    "required": ["packUnits"]
                }
                """;
            return objectMapper.readTree(schemaJson);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create prepaid schema", e);
        }
    }

    private static JsonNode createTieredSchema() {
        try {
            String schemaJson = """
                {
                    "type": "object",
                    "properties": {
                        "unitLabel": {
                            "type": "string",
                            "default": "token"
                        },
                        "tiersMode": {
                            "type": "string",
                            "enum": ["graduated", "volume"]
                        },
                        "billingPeriod": {
                            "type": "string",
                            "enum": ["day", "week", "month", "year"]
                        },
                        "tiers": {
                            "type": "array",
                            "items": {
                                "type": "object",
                                "properties": {
                                    "threshold": {
                                        "type": "number"
                                    },
                                    "unitAmount": {
                                        "type": "number"
                                    },
                                    "ordering": {
                                        "type": "integer"
                                    }
                                },
                                "required": ["threshold", "unitAmount", "ordering"]
                            },
                            "minItems": 1
                        }
                    },
                    "required": ["tiersMode", "billingPeriod", "tiers"]
                }
                """;
            return objectMapper.readTree(schemaJson);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create tiered schema", e);
        }
    }

    @lombok.Builder
    @lombok.Data
    private static class ConfigMetadata {
        private String configTableName;
        private Class<?> configClass;
        private JsonNode configSchema;
    }
}

