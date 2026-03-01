package com.bugisiw.marketplace.common.service;

import com.bugisiw.marketplace.common.model.billing.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Implementation of TotalUnitsCalculator that directly queries the database
 * without any caching mechanism.
 */
@Slf4j
@RequiredArgsConstructor
public class NonCachedTotalUnitsCalculator implements TotalUnitsCalculator {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public BigDecimal calculateTotalUnitsUsed(String userId, String userProductId, TimeUnit timeUnit, LocalDateTime subscriptionStartDate) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime periodStart = calculatePeriodStart(now, timeUnit, subscriptionStartDate);

        try {
            return jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(total_units_used), 0) FROM usage_logs " +
                            "WHERE user_id = ?::uuid AND user_product_id = ?::uuid AND created_at >= ?",
                    BigDecimal.class,
                    userId, userProductId, periodStart
            );
        } catch (Exception e) {
            log.error("Error getting used units: {}", e.getMessage(), e);
            return BigDecimal.ZERO.setScale(2, RoundingMode.UP);
        }
    }

    @Override
    public BigDecimal calculateTotalOverageCost(String userId, String userProductId, LocalDateTime subscriptionStartDate) {
        LocalDateTime cycleStart = subscriptionStartDate.withDayOfMonth(1);
        LocalDateTime now = LocalDateTime.now();
        while (cycleStart.isBefore(now)) {
            cycleStart = cycleStart.plusMonths(1);
        }
        cycleStart = cycleStart.minusMonths(1);

        log.info("[SPENDING_CAP_DEBUG] [{}] [DB_QUERY] calculateTotalOverageCost - userId: {}, userProductId: {}, cycleStart: {}, querying database for SUM(cost) where is_overage=true",
            Thread.currentThread().getName(), userId, userProductId, cycleStart);
        try {
            String result = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(cost), 0.0) FROM usage_logs " +
                            "WHERE user_id = ?::uuid AND user_product_id = ?::uuid AND created_at >= ? AND is_overage = true",
                    String.class,
                    userId, userProductId, cycleStart
            );
            BigDecimal totalOverageCost = result != null ? new BigDecimal(result).setScale(2, RoundingMode.UP) : BigDecimal.ZERO.setScale(2, RoundingMode.UP);
            log.info("[SPENDING_CAP_DEBUG] [{}] [DB_QUERY] calculateTotalOverageCost result - userId: {}, userProductId: {}, totalOverageCost: {}",
                Thread.currentThread().getName(), userId, userProductId, totalOverageCost);
            return totalOverageCost;
        } catch (Exception e) {
            log.error("[SPENDING_CAP_DEBUG] [{}] [DB_QUERY] Error getting total overage cost: {}", Thread.currentThread().getName(), e.getMessage(), e);
            return BigDecimal.ZERO.setScale(2, RoundingMode.UP);
        }
    }

    /**
     * Calculates the start date for the current billing period based on the time unit and subscription start date.
     */
    private LocalDateTime calculatePeriodStart(LocalDateTime now, TimeUnit periodUnit, LocalDateTime startDate) {
        // Default to MONTH if timeUnit is null (common for USAGE_POSTPAID with per-request billing)
        if (periodUnit == null) {
            periodUnit = TimeUnit.MONTH;
        }
        switch (periodUnit) {
            case MINUTE:
                return now.truncatedTo(ChronoUnit.MINUTES);
            case HOUR:
                return now.truncatedTo(ChronoUnit.HOURS);
            case DAY:
                return now.truncatedTo(ChronoUnit.DAYS);
            case WEEK:
                long weeksSinceStart = ChronoUnit.WEEKS.between(startDate, now);
                return startDate.plusWeeks(weeksSinceStart).truncatedTo(ChronoUnit.DAYS);
            case MONTH:
                long monthsSinceStart = ChronoUnit.MONTHS.between(
                        startDate.withDayOfMonth(1),
                        now.withDayOfMonth(1)
                );
                return startDate.plusMonths(monthsSinceStart).withDayOfMonth(startDate.getDayOfMonth()).truncatedTo(ChronoUnit.DAYS);
            case YEAR:
                long yearsSinceStart = ChronoUnit.YEARS.between(
                        startDate.withDayOfYear(1),
                        now.withDayOfYear(1)
                );
                return startDate.plusYears(yearsSinceStart).withDayOfYear(startDate.getDayOfYear()).truncatedTo(ChronoUnit.DAYS);
            default:
                return now.minusMonths(1);
        }
    }
} 