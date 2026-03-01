package com.bugisiw.marketplace.common.repository;

import com.bugisiw.marketplace.common.model.usage.StripeReportBatch;
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
 * Repository for managing Stripe report batches.
 * Uses JDBC for database operations (usage-service uses JDBC, not JPA).
 */
@Slf4j
@Repository
public class StripeReportBatchRepository {
    private final JdbcTemplate jdbcTemplate;

    public StripeReportBatchRepository(@Qualifier("usageJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Creates a new batch record.
     */
    public StripeReportBatch create(StripeReportBatch batch) {
        Instant now = Instant.now();
        if (batch.getCreatedAt() == null) {
            batch.setCreatedAt(now);
        }
        if (batch.getUpdatedAt() == null) {
            batch.setUpdatedAt(now);
        }

        String sql = "INSERT INTO stripe_report_batches " +
                "(batch_id, user_product_id, stripe_subscription_item_id, total_units, report_count, " +
                "report_status, error_message, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.update(sql,
                batch.getBatchId(),
                batch.getUserProductId(),
                batch.getStripeSubscriptionItemId(),
                batch.getTotalUnits(),
                batch.getReportCount(),
                batch.getReportStatus() != null ? batch.getReportStatus().name() : StripeReportStatus.PENDING.name(),
                batch.getErrorMessage(),
                Timestamp.from(batch.getCreatedAt()),
                Timestamp.from(batch.getUpdatedAt()));

        // Retrieve the generated ID
        String selectSql = "SELECT id FROM stripe_report_batches WHERE batch_id = ?";
        Long id = jdbcTemplate.queryForObject(selectSql, Long.class, batch.getBatchId());
        batch.setId(id);

        return batch;
    }

    /**
     * Finds batch by batch ID.
     */
    public StripeReportBatch findByBatchId(String batchId) {
        String sql = "SELECT * FROM stripe_report_batches WHERE batch_id = ?";
        List<StripeReportBatch> results = jdbcTemplate.query(sql, this::mapBatch, batchId);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Finds batches by user product ID.
     */
    public List<StripeReportBatch> findByUserProductId(UUID userProductId) {
        String sql = "SELECT * FROM stripe_report_batches WHERE user_product_id = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, this::mapBatch, userProductId);
    }

    /**
     * Updates batch status and Stripe usage record ID.
     */
    public void updateBatchStatus(String batchId, StripeReportStatus status, String stripeUsageRecordId) {
        String sql = "UPDATE stripe_report_batches " +
                "SET report_status = ?, stripe_usage_record_id = ?, reported_at = ?, updated_at = ? " +
                "WHERE batch_id = ?";

        jdbcTemplate.update(sql,
                status.name(),
                stripeUsageRecordId,
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()),
                batchId);
    }

    /**
     * Updates batch with error information.
     */
    public void updateBatchError(String batchId, String errorMessage) {
        String sql = "UPDATE stripe_report_batches " +
                "SET report_status = 'FAILED', error_message = ?, updated_at = ? " +
                "WHERE batch_id = ?";

        jdbcTemplate.update(sql,
                errorMessage,
                Timestamp.from(Instant.now()),
                batchId);
    }

    /**
     * Maps a ResultSet row to StripeReportBatch.
     */
    private StripeReportBatch mapBatch(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return StripeReportBatch.builder()
                .id(rs.getLong("id"))
                .batchId(rs.getString("batch_id"))
                .userProductId(rs.getObject("user_product_id", UUID.class))
                .stripeSubscriptionItemId(rs.getString("stripe_subscription_item_id"))
                .totalUnits(rs.getBigDecimal("total_units"))
                .reportCount(rs.getInt("report_count"))
                .stripeUsageRecordId(rs.getString("stripe_usage_record_id"))
                .reportStatus(parseStatus(rs.getString("report_status")))
                .errorMessage(rs.getString("error_message"))
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
            log.warn("Invalid batch status: {}", status);
            return StripeReportStatus.PENDING;
        }
    }
}

