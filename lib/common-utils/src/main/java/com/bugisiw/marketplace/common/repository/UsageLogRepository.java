package com.bugisiw.marketplace.common.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.bugisiw.marketplace.common.model.usage.UsageLog;
import com.bugisiw.marketplace.common.model.usage.JobStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.function.Supplier;
import java.math.BigDecimal;

/**
 * Repository for logging usage data to the database.
 * Uses a non-blocking approach with a BlockingQueue and virtual threads
 * to ensure high performance.
 */
@Slf4j
@Repository
public class UsageLogRepository {
    private final JdbcTemplate jdbcTemplate;
    private final BlockingQueue<UsageLog> logQueue;
    private final int queueWarningThreshold;
    private final int batchSize;
    private final int maxRetries;
    private final long initialRetryDelayMs;
    
    public UsageLogRepository(
            @Qualifier("usageJdbcTemplate") JdbcTemplate jdbcTemplate,
            @Value("${usage-logging.queue.capacity:10000}") int queueCapacity,
            @Value("${usage-logging.queue.warning-threshold:10000}") int queueWarningThreshold,
            @Value("${usage-logging.queue.batch-size:100}") int batchSize,
            @Value("${usage-logging.retry.max-retries:3}") int maxRetries,
            @Value("${usage-logging.retry.initial-delay-ms:100}") long initialRetryDelayMs) {
        this.jdbcTemplate = jdbcTemplate;
        this.queueWarningThreshold = queueWarningThreshold;
        this.batchSize = batchSize;
        this.maxRetries = maxRetries;
        this.initialRetryDelayMs = initialRetryDelayMs;
        this.logQueue = new LinkedBlockingQueue<>(queueCapacity);
        log.info("UsageLogRepository initialized with queue capacity: {}", logQueue.remainingCapacity() + logQueue.size());
    }

    /**
     * Synchronously logs a usage entry directly to the database.
     * This method blocks until the database operation completes.
     * Use when immediate persistence is required.
     * 
     * @param usageLog The usage log entry to be inserted
     * @return The generated ID of the inserted usage log, or null if insertion failed
     */
    public Long log(UsageLog usageLog) {
        log.debug("Synchronously writing usage log for requestId: {}", usageLog.getRequestId());
        if (usageLog == null) {
            log.warn("Attempted to log a null UsageLog entry");
            return null;
        }
        
        try {
            return insertLogWithRetry(usageLog);
        } catch (Exception e) {
            log.error("Failed to synchronously write usage log to database: {}", e.getMessage(), e);
            // Log the entry that failed to be written for manual recovery
            List<UsageLog> failedLogs = new ArrayList<>();
            failedLogs.add(usageLog);
            logFailedEntriesAsSql(failedLogs);
            return null;
        }
    }

    /**
     * Asynchronously logs a usage entry by adding it to the queue.
     * 
     * @param usageLog The usage log entry to be queued
     * @return true if the log was successfully queued, false otherwise
     */
/*    public boolean logAsync(UsageLog usageLog) {
        boolean success = logQueue.offer(usageLog);
        if (!success) {
            log.warn("Usage log queue full, dropping log: {}", usageLog);
        }
        
        int queueSize = logQueue.size();
        if (queueSize >= queueWarningThreshold) {
            log.warn("Usage log queue size exceeded threshold ({}): current size = {}", 
                    queueWarningThreshold, queueSize);
        }
        
        return success;
    }*/

    /**
     * This method flushes the log queue to the database.
     * It should be called regularly from each service.
     * Uses virtual threads for non-blocking database operations.
     */
    public void flushLogs() {
        List<UsageLog> logs = new ArrayList<>();
        logQueue.drainTo(logs);
        
        if (!logs.isEmpty()) {
            log.debug("Flushing {} usage logs to database", logs.size());
            
            // Use virtual threads for non-blocking database operations
            Executors.newVirtualThreadPerTaskExecutor().submit(() -> {
                try {
                    // Process in smaller batches to avoid overwhelming the database
                    for (int i = 0; i < logs.size(); i += batchSize) {
                        int endIndex = Math.min(i + batchSize, logs.size());
                        List<UsageLog> batch = logs.subList(i, endIndex);
                        
                        List<UsageLog> failedLogs = new ArrayList<>();
                        for (UsageLog logEntry : batch) {
                            Long insertedId = insertLogWithRetry(logEntry);
                            if (insertedId == null) {
                                failedLogs.add(logEntry);
                            }
                        }
                        
                        if (!failedLogs.isEmpty()) {
                            log.error("Failed to write {} usage logs after {} retries", failedLogs.size(), maxRetries);
                            // Log all failed entries for troubleshooting
                            logFailedEntriesAsSql(failedLogs);
                        }
                    }
                    log.debug("Successfully flushed {} usage logs to database", logs.size());
                } catch (Exception e) {
                    log.error("Error flushing usage logs to database", e);
                    // Log all entries that failed to be written
                    log.error("Failed to write {} usage logs due to exception", logs.size());
                    logFailedEntriesAsSql(logs);
                }
            });
        }
    }
    
    /**
     * Logs failed database entries as SQL insert statements that can be run in a Postgres console.
     * 
     * @param failedLogs the list of logs that failed to be inserted
     */
    private void logFailedEntriesAsSql(List<UsageLog> failedLogs) {
        log.error("SQL statements to manually insert failed log entries:");
        for (UsageLog logEntry : failedLogs) {
            StringBuilder sql = new StringBuilder();
            sql.append("INSERT INTO usage_logs (user_product_id, user_id, agent_id, agent_endpoint_id, request_id, request_type, ") 
               .append("billing_type, cumulative_units, total_units_used, base_units_used, ")
               .append("chargeable_overage_units_used, non_chargeable_overage_units_used, cost, ")
               .append("is_over_limit, is_overage, status, start_time, end_time, result, ")
               .append("result_url, content_type, error_message, http_status_code, is_agent_owner_invocation, is_billable, created_at, updated_at) VALUES (");
            
            // Format each value properly for SQL
            sql.append(formatSqlValue(logEntry.getUserProductId() != null ? logEntry.getUserProductId().toString() : null)).append(", ")
               .append(formatSqlValue(logEntry.getUserId() != null ? logEntry.getUserId().toString() : null)).append(", ")
               .append(formatSqlValue(logEntry.getAgentId() != null ? logEntry.getAgentId().toString() : null)).append(", ")
               .append(formatSqlValue(logEntry.getAgentEndpointId() != null ? logEntry.getAgentEndpointId().toString() : null)).append(", ")
               .append(formatSqlValue(logEntry.getRequestId())).append(", ")
               .append(formatSqlValue(logEntry.getRequestType() != null ? logEntry.getRequestType().name() : null)).append(", ")
               .append(formatSqlValue(logEntry.getBillingType())).append(", ")
               .append(formatSqlValue(logEntry.getCumulativeUnits() != null ? logEntry.getCumulativeUnits().toString() : null)).append(", ")
               .append(formatSqlValue(logEntry.getTotalUnitsUsed() != null ? logEntry.getTotalUnitsUsed().toString() : null)).append(", ")
               .append(formatSqlValue(logEntry.getBaseUnitsUsed() != null ? logEntry.getBaseUnitsUsed().toString() : null)).append(", ")
               .append(formatSqlValue(logEntry.getChargeableOverageUnitsUsed() != null ? logEntry.getChargeableOverageUnitsUsed().toString() : null)).append(", ")
               .append(formatSqlValue(logEntry.getNonChargeableOverageUnitsUsed() != null ? logEntry.getNonChargeableOverageUnitsUsed().toString() : null)).append(", ")
               .append(formatSqlValue(logEntry.getCost() != null ? logEntry.getCost().toString() : null)).append(", ")
               .append(logEntry.isOverLimit()).append(", ")
               .append(logEntry.isOverage()).append(", ")
               .append(formatSqlValue(logEntry.getStatus() != null ? logEntry.getStatus().name() : null)).append(", ")
               .append(formatTimestamp(logEntry.getStartTime())).append(", ")
               .append(formatTimestamp(logEntry.getEndTime())).append(", ")
               .append(formatSqlValue(logEntry.getResult())).append(", ")
               .append(formatSqlValue(logEntry.getResultUrl())).append(", ")
               .append(formatSqlValue(logEntry.getContentType())).append(", ")
               .append(formatSqlValue(logEntry.getErrorMessage())).append(", ")
               .append(logEntry.getHttpStatusCode() != null ? logEntry.getHttpStatusCode() : "NULL").append(", ")
               .append(logEntry.isAgentOwnerInvocation()).append(", ")
               .append(logEntry.getIsBillable() != null ? logEntry.getIsBillable() : true).append(", ")
               .append("CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);");
            
            log.error(sql.toString());
        }
    }
    
    /**
     * Formats a string value for SQL insertion by adding single quotes and escaping any internal single quotes.
     * 
     * @param value the string value to format
     * @return the properly formatted SQL string value or 'NULL' if value is null
     */
    private String formatSqlValue(String value) {
        if (value == null) {
            return "NULL";
        } else {
            // Escape single quotes by doubling them
            return "'" + value.replace("'", "''") + "'";
        }
    }
    
    /**
     * Formats an Instant for SQL insertion.
     * 
     * @param instant the Instant to format
     * @return the properly formatted SQL timestamp value or 'NULL' if instant is null
     */
    private String formatTimestamp(Instant instant) {
        if (instant == null) {
            return "NULL";
        } else {
            return "'" + Timestamp.from(instant).toString() + "'";
        }
    }
    
    /**
     * Insert a log entry with retry logic.
     * 
     * @param log the log entry to insert
     * @return true if successful, false if all retries failed
     */
    private Long insertLogWithRetry(UsageLog usageLogEntry) {
        Instant timestamp = Instant.now();
        
        return withRetry(() -> {
            org.springframework.jdbc.support.GeneratedKeyHolder keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
            
            jdbcTemplate.update(connection -> {
                java.sql.PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO usage_logs (user_product_id, user_id, agent_id, agent_endpoint_id, request_id, request_type, " +
                "billing_type, cumulative_units, total_units_used, base_units_used, " +
                "chargeable_overage_units_used, non_chargeable_overage_units_used, cost, " +
                "is_over_limit, is_overage, status, start_time, end_time, result, " +
                "result_url, content_type, error_message, http_status_code, is_agent_owner_invocation, is_billable, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS
                );
                int paramIndex = 1;
                ps.setObject(paramIndex++, usageLogEntry.getUserProductId());
                ps.setObject(paramIndex++, usageLogEntry.getUserId());
                ps.setObject(paramIndex++, usageLogEntry.getAgentId());
                ps.setObject(paramIndex++, usageLogEntry.getAgentEndpointId());
                ps.setString(paramIndex++, usageLogEntry.getRequestId());
                ps.setString(paramIndex++, usageLogEntry.getRequestType() != null ? usageLogEntry.getRequestType().name() : null);
                ps.setString(paramIndex++, usageLogEntry.getBillingType());
                ps.setBigDecimal(paramIndex++, usageLogEntry.getCumulativeUnits() != null ? usageLogEntry.getCumulativeUnits() : BigDecimal.ZERO);
                ps.setBigDecimal(paramIndex++, usageLogEntry.getTotalUnitsUsed() != null ? usageLogEntry.getTotalUnitsUsed() : BigDecimal.ZERO);
                ps.setBigDecimal(paramIndex++, usageLogEntry.getBaseUnitsUsed() != null ? usageLogEntry.getBaseUnitsUsed() : BigDecimal.ZERO);
                ps.setBigDecimal(paramIndex++, usageLogEntry.getChargeableOverageUnitsUsed());
                ps.setBigDecimal(paramIndex++, usageLogEntry.getNonChargeableOverageUnitsUsed());
                ps.setBigDecimal(paramIndex++, usageLogEntry.getCost());
                ps.setBoolean(paramIndex++, usageLogEntry.isOverLimit());
                ps.setBoolean(paramIndex++, usageLogEntry.isOverage());
                ps.setString(paramIndex++, usageLogEntry.getStatus() != null ? usageLogEntry.getStatus().name() : null);
                ps.setTimestamp(paramIndex++, usageLogEntry.getStartTime() != null ? Timestamp.from(usageLogEntry.getStartTime()) : null);
                ps.setTimestamp(paramIndex++, usageLogEntry.getEndTime() != null ? Timestamp.from(usageLogEntry.getEndTime()) : null);
                ps.setString(paramIndex++, usageLogEntry.getResult());
                ps.setString(paramIndex++, usageLogEntry.getResultUrl());
                ps.setString(paramIndex++, usageLogEntry.getContentType());
                ps.setString(paramIndex++, usageLogEntry.getErrorMessage());
                ps.setObject(paramIndex++, usageLogEntry.getHttpStatusCode());
                ps.setBoolean(paramIndex++, usageLogEntry.isAgentOwnerInvocation());
                ps.setBoolean(paramIndex++, usageLogEntry.getIsBillable() != null ? usageLogEntry.getIsBillable() : true);
                ps.setTimestamp(paramIndex++, Timestamp.from(timestamp));
                ps.setTimestamp(paramIndex++, Timestamp.from(timestamp));
                return ps;
            }, keyHolder);
            
            // H2 may return multiple keys (ID, CREATED_AT, UPDATED_AT), so we need to extract the ID
            java.util.Map<String, Object> keys = keyHolder.getKeys();
            if (keys != null && !keys.isEmpty()) {
                // Try different case variations for the ID column
                Object idValue = keys.get("ID");
                if (idValue == null) {
                    idValue = keys.get("id");
                }
                if (idValue == null) {
                    idValue = keys.get("Id");
                }
                if (idValue instanceof java.lang.Number) {
                    return ((java.lang.Number) idValue).longValue();
                }
            }
            return null;
        }, "insert log for request " + usageLogEntry.getRequestId());
    }
    
    /**
     * Retrieve usage logs for a specific user.
     * 
     * @param userId the user ID to search for
     * @return a list of usage logs for the specified user
     */
    public List<UsageLog> findByUserId(UUID userId) {
        return jdbcTemplate.query(
            "SELECT * FROM usage_logs WHERE user_id = ? ORDER BY created_at DESC",
            (rs, rowNum) -> mapUsageLog(rs),
            userId
        );
    }
    
    /**
     * Retrieve usage logs for a specific agent.
     * 
     * @param agentId the agent ID to search for
     * @return a list of usage logs for the specified agent
     */
    public List<UsageLog> findByAgentId(UUID agentId) {
        return jdbcTemplate.query(
            "SELECT * FROM usage_logs WHERE agent_id = ? ORDER BY created_at DESC",
            (rs, rowNum) -> mapUsageLog(rs),
            agentId
        );
    }
    
    /**
     * Retrieve a usage log by its request ID.
     * 
     * @param requestId the request ID to search for
     * @return the usage log for the specified request, or null if not found
     */
    public UsageLog findByRequestId(String requestId) {
        List<UsageLog> results = jdbcTemplate.query(
            "SELECT * FROM usage_logs WHERE request_id = ?",
            (rs, rowNum) -> mapUsageLog(rs),
            requestId
        );
        
        return results.isEmpty() ? null : results.get(0);
    }
    
    /**
     * Update the status of an existing usage log.
     * 
     * @param requestId the request ID to update
     * @param status the new status
     * @return true if the update was successful, false otherwise
     */
    public boolean updateStatus(String requestId, JobStatus status) {
        return withRetry(() -> {
            int rowsAffected = jdbcTemplate.update(
                "UPDATE usage_logs SET status = ?, updated_at = ? WHERE request_id = ?",
                status.name(),
                Timestamp.from(Instant.now()),
                requestId
            );
            return rowsAffected > 0;
        }, "update status for request " + requestId);
    }
    
    /**
     * Complete a usage log with results (no overage breakdown).
     * Delegates to {@link #completeLog(String, JobStatus, String, String, String, BigDecimal, BigDecimal, boolean, BigDecimal, Boolean, Boolean, BigDecimal, BigDecimal, BigDecimal)} with overage fields defaulted.
     */
    public boolean completeLog(String requestId, JobStatus status, String result,
                              String resultUrl, String contentType, BigDecimal unitsUsed, BigDecimal cost, boolean isBillable) {
        return completeLog(requestId, status, result, resultUrl, contentType, unitsUsed, cost, isBillable,
                null, null, null, null, null, null);
    }

    /**
     * Complete a usage log with results and optional overage fields.
     * When overage params are null, base_units_used defaults to unitsUsed, is_over_limit/is_overage to false, overage unit columns to zero.
     *
     * @param baseUnitsUsed base (included) units used this request; if null, uses unitsUsed
     * @param isOverLimit true if usage exceeded included limit
     * @param isOverage true if overage was allowed and applied
     * @param chargeableOverageUnitsUsed chargeable overage units
     * @param nonChargeableOverageUnitsUsed surplus (non-chargeable) overage units
     * @param cumulativeUnits total units used in the billing cycle after this request; if null, uses unitsUsed
     */
    public boolean completeLog(String requestId, JobStatus status, String result,
                              String resultUrl, String contentType, BigDecimal unitsUsed, BigDecimal cost, boolean isBillable,
                              BigDecimal baseUnitsUsed, Boolean isOverLimit, Boolean isOverage,
                              BigDecimal chargeableOverageUnitsUsed, BigDecimal nonChargeableOverageUnitsUsed,
                              BigDecimal cumulativeUnits) {
        BigDecimal effectiveUnits = unitsUsed != null ? unitsUsed : BigDecimal.ZERO;
        BigDecimal effectiveCost = cost != null ? cost : BigDecimal.ZERO;
        BigDecimal effectiveBase = baseUnitsUsed != null ? baseUnitsUsed : effectiveUnits;
        BigDecimal effectiveCumulative = cumulativeUnits != null ? cumulativeUnits : effectiveUnits;
        boolean overLimit = Boolean.TRUE.equals(isOverLimit);
        boolean overage = Boolean.TRUE.equals(isOverage);
        BigDecimal chargeable = chargeableOverageUnitsUsed != null ? chargeableOverageUnitsUsed : BigDecimal.ZERO;
        BigDecimal nonChargeable = nonChargeableOverageUnitsUsed != null ? nonChargeableOverageUnitsUsed : BigDecimal.ZERO;
        Boolean resultBoxed = withRetry(() -> {
            int rowsAffected = jdbcTemplate.update(
                "UPDATE usage_logs SET status = ?, result = ?, result_url = ?, " +
                "content_type = ?, total_units_used = ?, base_units_used = ?, cumulative_units = ?, " +
                "chargeable_overage_units_used = ?, non_chargeable_overage_units_used = ?, " +
                "cost = ?, is_billable = ?, is_over_limit = ?, is_overage = ?, end_time = ?, updated_at = ? " +
                "WHERE request_id = ?",
                status.name(),
                result,
                resultUrl,
                contentType,
                effectiveUnits,
                effectiveBase,
                effectiveCumulative,
                chargeable,
                nonChargeable,
                effectiveCost,
                isBillable,
                overLimit,
                overage,
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()),
                requestId
            );
            if (rowsAffected > 0) {
                try {
                    BigDecimal readBack = jdbcTemplate.queryForObject(
                        "SELECT total_units_used FROM usage_logs WHERE request_id = ?",
                        BigDecimal.class,
                        requestId
                    );
                    log.info(">>>>> DEBUG completeLog - after UPDATE, read back total_units_used: {} for requestId: {} (expected: {})",
                            readBack, requestId, effectiveUnits);
                } catch (Exception e) {
                    log.warn(">>>>> DEBUG completeLog - read-back failed for requestId {}: {}", requestId, e.getMessage());
                }
            }
            return rowsAffected > 0;
        }, "complete log for request " + requestId);
        return Boolean.TRUE.equals(resultBoxed);
    }
    
    /**
     * Generic method to retry an operation with exponential backoff.
     * 
     * @param operation the operation to retry
     * @param operationDescription description for logging
     * @return the result of the operation, or false if all retries failed
     */
    private <T> T withRetry(Supplier<T> operation, String operationDescription) {
        int attempts = 0;
        long delay = initialRetryDelayMs;
        
        while (attempts < maxRetries) {
            try {
                return operation.get();
            } catch (Exception e) {
                attempts++;
                if (attempts >= maxRetries) {
                    log.error("Failed to {} after {} attempts: {}", 
                            operationDescription, maxRetries, e.getMessage());
                    log.error("Final exception details", e);
                    return null;
                } else {
                    // Log more detailed error information for SQL exceptions
                    if (e instanceof org.springframework.jdbc.BadSqlGrammarException) {
                        org.springframework.jdbc.BadSqlGrammarException sqlEx = (org.springframework.jdbc.BadSqlGrammarException) e;
                        log.warn("Attempt {} to {} failed with SQL error: {}, SQL state: {}, Error code: {}, SQL: {}", 
                                attempts, operationDescription, e.getMessage(), 
                                sqlEx.getSQLException().getSQLState(),
                                sqlEx.getSQLException().getErrorCode(),
                                sqlEx.getSql());
                    } else if (e instanceof org.springframework.dao.DataIntegrityViolationException) {
                        log.warn("Attempt {} to {} failed with data integrity violation: {}, Root cause: {}", 
                                attempts, operationDescription, e.getMessage(),
                                e.getCause() != null ? e.getCause().getMessage() : "unknown");
                    } else {
                        log.warn("Attempt {} to {} failed: {}, Exception type: {}", 
                                attempts, operationDescription, e.getMessage(), e.getClass().getName());
                    }
                    
                    try {
                        Thread.sleep(delay);
                        // Exponential backoff
                        delay *= 2;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("Retry sleep interrupted");
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Get the current size of the log queue.
     * 
     * @return the current size of the log queue
     */
    public int getQueueSize() {
        return logQueue.size();
    }
    
    // Helper method to map a ResultSet to a UsageLog object
    private UsageLog mapUsageLog(java.sql.ResultSet rs) throws java.sql.SQLException {
        return UsageLog.builder()
            .id(rs.getLong("id"))
            .userProductId(rs.getObject("user_product_id", UUID.class))
            .userId(rs.getObject("user_id", UUID.class))
            .agentId(rs.getObject("agent_id", UUID.class))
            .agentEndpointId(rs.getObject("agent_endpoint_id", UUID.class))
            .requestId(rs.getString("request_id"))
            .requestType(parseEnum(rs.getString("request_type"), com.bugisiw.marketplace.common.model.usage.RequestType.class))
            .billingType(rs.getString("billing_type"))
            .cumulativeUnits(rs.getBigDecimal("cumulative_units"))
            .totalUnitsUsed(rs.getBigDecimal("total_units_used"))
            .baseUnitsUsed(rs.getBigDecimal("base_units_used"))
            .chargeableOverageUnitsUsed(rs.getBigDecimal("chargeable_overage_units_used"))
            .nonChargeableOverageUnitsUsed(rs.getBigDecimal("non_chargeable_overage_units_used"))
            .cost(rs.getBigDecimal("cost"))
            .isOverLimit(rs.getBoolean("is_over_limit"))
            .isOverage(rs.getBoolean("is_overage"))
            .status(parseEnum(rs.getString("status"), com.bugisiw.marketplace.common.model.usage.JobStatus.class))
            .startTime(rs.getTimestamp("start_time") != null ? rs.getTimestamp("start_time").toInstant() : null)
            .endTime(rs.getTimestamp("end_time") != null ? rs.getTimestamp("end_time").toInstant() : null)
            .result(rs.getString("result"))
            .resultUrl(rs.getString("result_url"))
            .contentType(rs.getString("content_type"))
            .errorMessage(rs.getString("error_message"))
            .httpStatusCode(rs.getObject("http_status_code") != null ? rs.getInt("http_status_code") : null)
            .isAgentOwnerInvocation(rs.getBoolean("is_agent_owner_invocation"))
            .isBillable(rs.getBoolean("is_billable"))
            .createdAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null)
            .updatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null)
            .build();
    }
    
    // Helper method to safely parse enums from strings
    private <T extends Enum<T>> T parseEnum(String value, Class<T> enumClass) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid enum value {} for type {}", value, enumClass.getSimpleName());
            return null;
        }
    }
} 