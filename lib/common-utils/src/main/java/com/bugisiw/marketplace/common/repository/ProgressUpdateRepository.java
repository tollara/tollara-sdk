package com.bugisiw.marketplace.common.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

import com.bugisiw.marketplace.common.model.usage.ProgressUpdate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;

/**
 * Repository for logging progress updates to the database.
 * Uses a non-blocking approach with a BlockingQueue and virtual threads
 * to batch process updates every 5 seconds.
 */
@Slf4j
@Repository
public class ProgressUpdateRepository {
    private final JdbcTemplate jdbcTemplate;
    private final BlockingQueue<ProgressUpdate> updateQueue;
    private final int batchSize;
    private final int maxRetries;
    private final long initialRetryDelayMs;
    
    public ProgressUpdateRepository(
            @Qualifier("usageJdbcTemplate") JdbcTemplate jdbcTemplate,
            @Value("${usage-logging.progress.queue.capacity:10000}") int queueCapacity,
            @Value("${usage-logging.queue.batch-size:100}") int batchSize,
            @Value("${usage-logging.retry.max-retries:3}") int maxRetries,
            @Value("${usage-logging.retry.initial-delay-ms:100}") long initialRetryDelayMs) {
        this.jdbcTemplate = jdbcTemplate;
        this.batchSize = batchSize;
        this.maxRetries = maxRetries;
        this.initialRetryDelayMs = initialRetryDelayMs;
        this.updateQueue = new LinkedBlockingQueue<>(queueCapacity);
        log.info("ProgressUpdateRepository initialized with queue capacity: {}", updateQueue.remainingCapacity() + updateQueue.size());
    }

    /**
     * Asynchronously logs a progress update by adding it to the queue.
     * 
     * @param update The progress update to be queued
     * @return true if the update was successfully queued, false otherwise
     */
    public boolean logAsync(ProgressUpdate update) {
        boolean success = updateQueue.offer(update);
        if (!success) {
            log.warn("Progress update queue full, dropping update: {}", update);
        }
        return success;
    }

    /**
     * Scheduled method to flush the progress update queue to the database.
     * Runs every 5 seconds.
     */
    @Scheduled(fixedDelayString = "${usage-logging.progress.flush-interval-ms:5000}")
    public void flushUpdates() {
        List<ProgressUpdate> updates = new ArrayList<>();
        updateQueue.drainTo(updates);
        
        if (!updates.isEmpty()) {
            log.debug("Flushing {} progress updates to database", updates.size());
            
            try {
                // Process in smaller batches to avoid overwhelming the database
                for (int i = 0; i < updates.size(); i += batchSize) {
                    int endIndex = Math.min(i + batchSize, updates.size());
                    List<ProgressUpdate> batch = updates.subList(i, endIndex);
                    
                    List<ProgressUpdate> failedUpdates = new ArrayList<>();
                    for (ProgressUpdate update : batch) {
                        boolean success = insertUpdateWithRetry(update);
                        if (!success) {
                            failedUpdates.add(update);
                        }
                    }
                    
                    if (!failedUpdates.isEmpty()) {
                        log.error("Failed to write {} progress updates after {} retries", failedUpdates.size(), maxRetries);
                        // Log a sample of failed entries for troubleshooting
                        int samplesToLog = Math.min(5, failedUpdates.size());
                        for (int j = 0; j < samplesToLog; j++) {
                            ProgressUpdate failedUpdate = failedUpdates.get(j);
                            log.error("Failed progress update {}: requestId={}, stage={}", 
                                    j + 1, failedUpdate.getRequestId(), failedUpdate.getStage());
                        }
                    }
                }
                log.debug("Successfully flushed {} progress updates to database", updates.size());
            } catch (Exception e) {
                log.error("Error flushing progress updates to database", e);
            }
        }
    }
    
    /**
     * Insert a progress update with retry logic.
     * 
     * @param update the update to insert
     * @return true if successful, false if all retries failed
     */
    private boolean insertUpdateWithRetry(ProgressUpdate update) {
        Instant timestamp = update.getTimestamp() != null ? update.getTimestamp() : Instant.now();

        Boolean result = withRetry(() -> {
            jdbcTemplate.update(
                "INSERT INTO usage_progress (request_id, stage, error_message, percentage_complete, created_at) " +
                "VALUES (?, ?, ?, ?, ?)",
                update.getRequestId(),
                update.getStage(),
                update.getErrorMessage(),
                update.getPercentageComplete(),
                Timestamp.from(timestamp)
            );
            return true;
        }, "insert progress update for request " + update.getRequestId());
        return Boolean.TRUE.equals(result);
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
                    return null;
                } else {
                    log.warn("Attempt {} to {} failed: {}, retrying...", 
                            attempts, operationDescription, e.getMessage());
                    
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
     * Get the current size of the update queue.
     * 
     * @return the current size of the update queue
     */
    public int getQueueSize() {
        return updateQueue.size();
    }
    
    /**
     * Retrieve progress updates for a specific request.
     * 
     * @param requestId the request ID to search for
     * @return a list of progress updates for the specified request
     */
    public List<ProgressUpdate> findByRequestId(String requestId) {
        return jdbcTemplate.query(
            "SELECT * FROM usage_progress WHERE request_id = ? ORDER BY created_at ASC",
            (rs, rowNum) -> mapProgressUpdate(rs),
            requestId
        );
    }
    
    // Helper method to map a ResultSet to a ProgressUpdate object
    private ProgressUpdate mapProgressUpdate(java.sql.ResultSet rs) throws java.sql.SQLException {
        return ProgressUpdate.builder()
            .id(rs.getLong("id"))
            .requestId(rs.getString("request_id"))
            .stage(rs.getString("stage"))
            .errorMessage(rs.getString("error_message"))
            .percentageComplete(rs.getInt("percentage_complete"))
            .timestamp(rs.getTimestamp("created_at").toInstant())
            .build();
    }
} 