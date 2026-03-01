package com.bugisiw.marketplace.common.model.gateway;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a progress update for a long-running job.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressUpdate {
    /**
     * Unique identifier for the progress update.
     */
    private long id;
    
    /**
     * ID of the request this update is for.
     */
    private String requestId;
    
    /**
     * Current processing stage.
     */
    private String stage;
    
    /**
     * Percentage of completion (0-100).
     */
    private int percentageComplete;
    
    /**
     * Error message if there was an error.
     */
    private String errorMessage;
    
    /**
     * Time when this update was created.
     */
    private Instant timestamp;
} 