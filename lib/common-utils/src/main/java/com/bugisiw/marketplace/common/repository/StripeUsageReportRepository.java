package com.bugisiw.marketplace.common.repository;

import com.bugisiw.marketplace.common.model.usage.StripeUsageReport;
import com.bugisiw.marketplace.common.model.usage.StripeReportStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for managing Stripe usage reports.
 * Uses JDBC for database operations (usage-service uses JDBC, not JPA).
 */
@Slf4j
@Repository
public class StripeUsageReportRepository {
    private final JdbcTemplate jdbcTemplate;

    public StripeUsageReportRepository(@Qualifier("usageJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Creates a new usage report record.
     */
    public StripeUsageReport create(StripeUsageReport report) {
        Instant now = Instant.now();
        if (report.getCreatedAt() == null) {
            report.setCreatedAt(now);
        }
        if (report.getUpdatedAt() == null) {
            report.setUpdatedAt(now);
        }
        if (report.getRetryCount() == null) {
            report.setRetryCount(0);
        }

        String sql = "INSERT INTO stripe_usage_reports " +
                "(usage_log_id, batch_id, user_product_id, stripe_subscription_item_id, request_id, " +
                "units, report_status, idempotency_key, error_message, retry_count, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.update(sql,
                report.getUsageLogId(),
                report.getBatchId(),
                report.getUserProductId(),
                report.getStripeSubscriptionItemId(),
                report.getRequestId(),
                report.getUnits(),
                report.getReportStatus() != null ? report.getReportStatus().name() : StripeReportStatus.PENDING.name(),
                report.getIdempotencyKey(),
                report.getErrorMessage(),
                report.getRetryCount(),
                Timestamp.from(report.getCreatedAt()),
                Timestamp.from(report.getUpdatedAt()));

        // Retrieve the generated ID
        String selectSql = "SELECT id FROM stripe_usage_reports WHERE idempotency_key = ?";
        Long id = jdbcTemplate.queryForObject(selectSql, Long.class, report.getIdempotencyKey());
        report.setId(id);

        return report;
    }

    /**
     * Finds pending reports ordered by user product ID and creation time.
     * Used for batching - reports are grouped by user_product_id in the batch processor.
     */
    public List<StripeUsageReport> findPendingReportsGroupedByItem(int limit) {
        String sql = "SELECT * FROM stripe_usage_reports " +
                "WHERE report_status = 'PENDING' " +
                "ORDER BY user_product_id, created_at ASC " +
                "LIMIT ?";

        log.info(">>>>> DEBUG: Querying for pending reports with limit: {}", limit);
        List<StripeUsageReport> results = jdbcTemplate.query(sql, this::mapReport, limit);
        log.info(">>>>> DEBUG: Found {} pending report(s)", results.size());
        if (!results.isEmpty()) {
            log.info(">>>>> DEBUG: Sample pending report - ID: {}, userProductId: {}, subscriptionItemId: {}, units: {}", 
                results.get(0).getId(), results.get(0).getUserProductId(), 
                results.get(0).getStripeSubscriptionItemId(), results.get(0).getUnits());
        }
        return results;
    }

    /**
     * Finds pending reports for a specific subscription item, up to the specified limit.
     */
    public List<StripeUsageReport> findPendingReportsByItem(String stripeSubscriptionItemId, int limit) {
        String sql = "SELECT * FROM stripe_usage_reports " +
                "WHERE report_status = 'PENDING' AND stripe_subscription_item_id = ? " +
                "ORDER BY created_at ASC " +
                "LIMIT ?";

        return jdbcTemplate.query(sql, this::mapReport, stripeSubscriptionItemId, limit);
    }

    /**
     * Finds reports by batch ID.
     */
    public List<StripeUsageReport> findByBatchId(String batchId) {
        String sql = "SELECT * FROM stripe_usage_reports WHERE batch_id = ? ORDER BY created_at ASC";
        return jdbcTemplate.query(sql, this::mapReport, batchId);
    }

    /**
     * Finds report by usage log ID.
     */
    public StripeUsageReport findByUsageLogId(Long usageLogId) {
        String sql = "SELECT * FROM stripe_usage_reports WHERE usage_log_id = ?";
        List<StripeUsageReport> results = jdbcTemplate.query(sql, this::mapReport, usageLogId);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Updates report status and related fields.
     */
    public void updateReportStatus(Long reportId, StripeReportStatus status, String stripeUsageRecordId, String batchId) {
        String sql = "UPDATE stripe_usage_reports " +
                "SET report_status = ?, stripe_usage_record_id = ?, batch_id = ?, " +
                "reported_at = ?, updated_at = ? " +
                "WHERE id = ?";

        jdbcTemplate.update(sql,
                status.name(),
                stripeUsageRecordId,
                batchId,
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()),
                reportId);
    }

    /**
     * Updates report with error information.
     */
    public void updateReportError(Long reportId, String errorMessage, int retryCount) {
        String sql = "UPDATE stripe_usage_reports " +
                "SET report_status = 'FAILED', error_message = ?, retry_count = ?, " +
                "last_retry_at = ?, updated_at = ? " +
                "WHERE id = ?";

        jdbcTemplate.update(sql,
                errorMessage,
                retryCount,
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()),
                reportId);
    }

    /**
     * Maps a ResultSet row to StripeUsageReport.
     */
    private StripeUsageReport mapReport(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return StripeUsageReport.builder()
                .id(rs.getLong("id"))
                .usageLogId(rs.getLong("usage_log_id"))
                .batchId(rs.getString("batch_id"))
                .userProductId(rs.getObject("user_product_id", UUID.class))
                .stripeSubscriptionItemId(rs.getString("stripe_subscription_item_id"))
                .requestId(rs.getString("request_id"))
                .units(rs.getBigDecimal("units"))
                .reportStatus(parseStatus(rs.getString("report_status")))
                .stripeUsageRecordId(rs.getString("stripe_usage_record_id"))
                .idempotencyKey(rs.getString("idempotency_key"))
                .errorMessage(rs.getString("error_message"))
                .retryCount(rs.getInt("retry_count"))
                .lastRetryAt(rs.getTimestamp("last_retry_at") != null ? rs.getTimestamp("last_retry_at").toInstant() : null)
                .reportedAt(rs.getTimestamp("reported_at") != null ? rs.getTimestamp("reported_at").toInstant() : null)
                .createdAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null)
                .updatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null)
                .build();
    }

    private StripeReportStatus parseStatus(String status) {
        if (status == null) {
            return StripeReportStatus.PENDING;
        }
        try {
            return StripeReportStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid report status: {}", status);
            return StripeReportStatus.PENDING;
        }
    }
}

