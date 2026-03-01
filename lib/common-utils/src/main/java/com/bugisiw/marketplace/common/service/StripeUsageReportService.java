package com.bugisiw.marketplace.common.service;

import com.bugisiw.marketplace.common.model.billing.BillingModelType;
import com.bugisiw.marketplace.common.model.billing.Subscription;
import com.bugisiw.marketplace.common.model.usage.StripeReportStatus;
import com.bugisiw.marketplace.common.model.usage.StripeUsageReport;
import com.bugisiw.marketplace.common.model.usage.UsageLog;
import com.bugisiw.marketplace.common.repository.StripeUsageReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Service for queuing usage reports to Stripe.
 * Creates stripe_usage_reports records that will be processed by the batch processor.
 * This is a shared component used by both gateway-service and usage-service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeUsageReportService {

    private final StripeUsageReportRepository stripeUsageReportRepository;

    /**
     * Queues overage usage for reporting to Stripe (for SUBSCRIPTION model).
     * Only queues if there are chargeable overage units.
     * 
     * @param usageLog The usage log containing overage information
     * @param subscription The subscription with metered subscription item ID
     */
    @Transactional
    public void queueOverageForReporting(UsageLog usageLog, Subscription subscription) {
        log.info(">>>>> DEBUG: queueOverageForReporting called - requestId: {}, subscriptionId: {}, billingModelType: {}", 
                usageLog.getRequestId(), subscription.getId(), subscription.getBillingModelType());
        
        if (subscription.getBillingModelType() != BillingModelType.SUBSCRIPTION) {
            log.info(">>>>> DEBUG: Skipping overage queueing - not SUBSCRIPTION model (actual: {})", subscription.getBillingModelType());
            return;
        }

        log.info(">>>>> DEBUG: Checking metered subscription item ID - value: {}", subscription.getStripeMeteredSubscriptionItemId());
        if (subscription.getStripeMeteredSubscriptionItemId() == null || subscription.getStripeMeteredSubscriptionItemId().isEmpty()) {
            log.warn(">>>>> DEBUG: Cannot queue overage - metered subscription item ID is missing for subscription {}", subscription.getId());
            return;
        }

        log.info(">>>>> DEBUG: Checking overage conditions - isOverage: {}, chargeableOverageUnitsUsed: {}", 
                usageLog.isOverage(), usageLog.getChargeableOverageUnitsUsed());
        if (!usageLog.isOverage() || usageLog.getChargeableOverageUnitsUsed() == null || 
            usageLog.getChargeableOverageUnitsUsed().compareTo(BigDecimal.ZERO) <= 0) {
            log.info(">>>>> DEBUG: Skipping overage queueing - no chargeable overage units for usage log with requestId {} (isOverage: {}, chargeableOverageUnitsUsed: {})", 
                    usageLog.getRequestId(), usageLog.isOverage(), usageLog.getChargeableOverageUnitsUsed());
            return;
        }

        // Get the usage log ID - it should already be set since it was inserted synchronously
        log.info(">>>>> DEBUG: Getting usage log ID - current usageLog.getId(): {}", usageLog.getId());
        Long usageLogId = getUsageLogId(usageLog);
        log.info(">>>>> DEBUG: Retrieved usage log ID: {}", usageLogId);
        if (usageLogId == null) {
            log.warn(">>>>> DEBUG: Could not retrieve usage log ID for requestId {} - skipping Stripe report queueing. Will be caught by reconciliation job.", 
                    usageLog.getRequestId());
            return;
        }

        String idempotencyKey = generateIdempotencyKey(usageLog.getRequestId(), "overage");
        log.info(">>>>> DEBUG: Generated idempotency key: {}", idempotencyKey);

        StripeUsageReport report = StripeUsageReport.builder()
                .usageLogId(usageLogId)
                .userProductId(subscription.getId())
                .stripeSubscriptionItemId(subscription.getStripeMeteredSubscriptionItemId())
                .requestId(usageLog.getRequestId())
                .units(usageLog.getChargeableOverageUnitsUsed())
                .reportStatus(StripeReportStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .retryCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        log.info(">>>>> DEBUG: About to create StripeUsageReport - usageLogId: {}, userProductId: {}, subscriptionItemId: {}, units: {}", 
                usageLogId, subscription.getId(), subscription.getStripeMeteredSubscriptionItemId(), usageLog.getChargeableOverageUnitsUsed());

        try {
            stripeUsageReportRepository.create(report);
            log.info(">>>>> DEBUG: Successfully created StripeUsageReport with ID: {}", report.getId());
            log.info("Queued overage usage report: {} units for subscription item {} (usageLogId: {}, requestId: {})", 
                    report.getUnits(), report.getStripeSubscriptionItemId(), usageLogId, usageLog.getRequestId());
        } catch (Exception e) {
            log.error(">>>>> DEBUG: Exception creating StripeUsageReport: {}", e.getMessage(), e);
            log.error("Failed to queue overage usage report for usage log {} (requestId: {})", 
                    usageLogId, usageLog.getRequestId(), e);
            // Don't throw - we don't want to fail the usage logging if queueing fails
            // The reconciliation job will catch missing reports
        }
    }

    /**
     * Queues all usage for reporting to Stripe (for USAGE_POSTPAID model).
     * 
     * @param usageLog The usage log containing usage information
     * @param subscription The subscription with metered subscription item ID
     */
    @Transactional
    public void queueUsageForReporting(UsageLog usageLog, Subscription subscription) {
        if (subscription.getBillingModelType() != BillingModelType.USAGE_POSTPAID) {
            log.debug("Skipping usage queueing - not USAGE_POSTPAID model");
            return;
        }

        if (subscription.getStripeMeteredSubscriptionItemId() == null || subscription.getStripeMeteredSubscriptionItemId().isEmpty()) {
            log.warn("Cannot queue usage - metered subscription item ID is missing for subscription {}", subscription.getId());
            return;
        }

        if (usageLog.getTotalUnitsUsed() == null || usageLog.getTotalUnitsUsed().compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("Skipping usage queueing - no units to report for usage log with requestId {}", usageLog.getRequestId());
            return;
        }

        // Get the usage log ID - it should already be set since it was inserted synchronously
        Long usageLogId = getUsageLogId(usageLog);
        if (usageLogId == null) {
            log.warn("Could not retrieve usage log ID for requestId {} - skipping Stripe report queueing. Will be caught by reconciliation job.", 
                    usageLog.getRequestId());
            return;
        }

        String idempotencyKey = generateIdempotencyKey(usageLog.getRequestId(), "usage");

        StripeUsageReport report = StripeUsageReport.builder()
                .usageLogId(usageLogId)
                .userProductId(subscription.getId())
                .stripeSubscriptionItemId(subscription.getStripeMeteredSubscriptionItemId())
                .requestId(usageLog.getRequestId())
                .units(usageLog.getTotalUnitsUsed())
                .reportStatus(StripeReportStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .retryCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        try {
            stripeUsageReportRepository.create(report);
            log.info("Queued usage report: {} units for subscription item {} (usageLogId: {}, requestId: {})", 
                    report.getUnits(), report.getStripeSubscriptionItemId(), usageLogId, usageLog.getRequestId());
        } catch (Exception e) {
            log.error("Failed to queue usage report for usage log {} (requestId: {})", 
                    usageLogId, usageLog.getRequestId(), e);
            // Don't throw - we don't want to fail the usage logging if queueing fails
            // The reconciliation job will catch missing reports
        }
    }

    /**
     * Gets the usage log ID from the usage log object.
     * The usage log should already have an ID since it was inserted synchronously.
     * 
     * @param usageLog The usage log (should have ID set)
     * @return The usage log ID, or null if not set
     */
    private Long getUsageLogId(UsageLog usageLog) {
        // The usage log should already have an ID since it was inserted synchronously
        long currentId = usageLog.getId();
        if (currentId > 0) {
            return currentId;
        }
        
        log.warn("Usage log ID not set for requestId {} - this should not happen with synchronous inserts", 
                usageLog.getRequestId());
        return null;
    }

    /**
     * Generates a unique idempotency key for Stripe API calls.
     * Format: {requestId}-{type}-{timestamp}
     */
    private String generateIdempotencyKey(String requestId, String type) {
        // Use requestId + type + timestamp hash for uniqueness
        String base = requestId + "-" + type + "-" + Instant.now().toEpochMilli();
        // Stripe idempotency keys must be <= 255 chars
        if (base.length() > 255) {
            base = base.substring(0, 255);
        }
        return base;
    }
}

