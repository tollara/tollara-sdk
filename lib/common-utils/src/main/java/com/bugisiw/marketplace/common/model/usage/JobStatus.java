package com.bugisiw.marketplace.common.model.usage;

/**
 * Enum representing the status of a job or request.
 */
public enum JobStatus {
    /**
     * The job is waiting to be processed.
     */
    PENDING,
    
    /**
     * The job is currently being processed.
     */
    RUNNING,
    
    /**
     * The job has completed successfully.
     */
    COMPLETED,
    
    /**
     * The job has failed during processing.
     */
    FAILED
} 