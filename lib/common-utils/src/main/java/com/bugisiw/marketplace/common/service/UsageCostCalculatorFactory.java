package com.bugisiw.marketplace.common.service;

import com.bugisiw.marketplace.common.model.billing.BillingModelType;
import com.bugisiw.marketplace.common.model.billing.MeasurementType;
import com.bugisiw.marketplace.common.model.billing.Subscription;
import com.bugisiw.marketplace.common.model.billing.TierDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Factory for creating UsageCostCalculator implementations based on the subscription's billing type.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UsageCostCalculatorFactory {
    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates a UsageCostCalculator implementation based on the subscription's billing model type and unit label.
     *
     * @param subscription The subscription containing billing details.
     * @return The appropriate UsageCostCalculator implementation.
     * @throws IllegalArgumentException if the billing model type is unsupported.
     */
    public UsageCostCalculator createCalculator(Subscription subscription) {
        if (subscription == null) {
            throw new IllegalArgumentException("Subscription cannot be null");
        }
        
        BillingModelType billingModelType = subscription.getBillingModelType();
        String unitLabel = subscription.getUnitLabel();
        List<TierDto> tiers = subscription.getTiers();
        
        log.debug("Creating calculator for billingModelType: {}, unitLabel: {}, tiers: {}, agentProductId: {}", 
            billingModelType, unitLabel, tiers != null ? tiers.size() + " tiers" : "null", subscription.getAgentProductId());
        
        // Handle tiered USAGE_POSTPAID
        // Check if tiers are populated, or if we need to load them from database (fallback for when subscription mapping didn't populate them)
        if (billingModelType == BillingModelType.USAGE_POSTPAID) {
            // If tiers are not populated, try to load them from database as fallback
            if ((tiers == null || tiers.isEmpty()) && subscription.getAgentProductId() != null && jdbcTemplate != null) {
                log.warn("Tiers not populated in subscription for product {}, attempting to load from database", subscription.getAgentProductId());
                try {
                    // Check if tiered config exists
                    Integer tieredConfigCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM agent_product_tiered_config WHERE agent_product_id = ?",
                        Integer.class,
                        subscription.getAgentProductId()
                    );
                    
                    if (tieredConfigCount != null && tieredConfigCount > 0) {
                        // Load tiers from database
                        List<Map<String, Object>> tierRows = jdbcTemplate.queryForList(
                            "SELECT threshold, unit_amount, ordering FROM agent_product_tiered_tiers WHERE agent_product_id = ? ORDER BY ordering",
                            subscription.getAgentProductId()
                        );
                        
                        if (!tierRows.isEmpty()) {
                            tiers = tierRows.stream()
                                .map(row -> TierDto.builder()
                                    .threshold((BigDecimal) row.get("threshold"))
                                    .unitAmount((BigDecimal) row.get("unit_amount"))
                                    .ordering(((Number) row.get("ordering")).intValue())
                                    .build())
                                .toList();
                            log.info("Loaded {} tiers from database for product {}", tiers.size(), subscription.getAgentProductId());
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to load tiers from database for product {}: {}", subscription.getAgentProductId(), e.getMessage());
                }
            }
            
            // If we have tiers now, use tiered calculator
            if (tiers != null && !tiers.isEmpty()) {
                log.debug("Using TieredUsageCostCalculator for tiered USAGE_POSTPAID with {} tiers", tiers.size());
                return new TieredUsageCostCalculator();
            }
        }
        
        // Only SUBSCRIPTION and USAGE_POSTPAID (non-tiered) use these calculators
        if (billingModelType == BillingModelType.SUBSCRIPTION ||
            billingModelType == BillingModelType.USAGE_POSTPAID) {

            MeasurementType measurementType = subscription.getMeasurementType();
            if (measurementType == null) {
                measurementType = MeasurementTypeResolver.fromUnitLabel(unitLabel);
            }

            switch (measurementType) {
                case PER_TIME_UNIT:
                    log.debug("Using PerTimeUnitUsageCostCalculator for {}", billingModelType);
                    return new PerTimeUnitUsageCostCalculator();
                case PER_TOKEN:
                    log.debug("Using PerTokenUsageCostCalculator for {}", billingModelType);
                    return new PerTokenUsageCostCalculator();
                case PER_BYTE:
                    log.debug("Using PerByteUsageCostCalculator for {}", billingModelType);
                    return new PerByteUsageCostCalculator();
                case PER_REQUEST:
                default:
                    log.debug("Using PerRequestUsageCostCalculator for {}", billingModelType);
                    return new PerRequestUsageCostCalculator();
            }
        }

        throw new IllegalArgumentException("Unsupported billing model type for UsageCostCalculator: " + billingModelType);
    }
} 