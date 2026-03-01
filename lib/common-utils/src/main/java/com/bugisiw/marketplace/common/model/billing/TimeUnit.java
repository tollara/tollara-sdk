package com.bugisiw.marketplace.common.model.billing;

/**
 * Enumeration of time units for billing purposes.
 */
public enum TimeUnit {
    SECOND(1),
    MINUTE(60),
    HOUR(3600),
    DAY(86400),      // 24 hours
    WEEK(604800),    // 7 days
    MONTH(2592000),  // 30 days
    YEAR(31536000);  // 365 days

    private final long seconds;

    TimeUnit(long seconds) {
        this.seconds = seconds;
    }

    /**
     * Returns the number of seconds represented by this time unit.
     * 
     * @return the number of seconds
     */
    public long toSeconds() {
        return seconds;
    }
} 