package com.bugisiw.marketplace.common.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * Utility class for date operations used across microservices.
 * All dates are standardized to use Instant (UTC) for consistency.
 */
public class DateUtils {
    
    private DateUtils() {
        // Private constructor to prevent instantiation
    }
    
    /**
     * Converts a Date to an Instant.
     *
     * @param date the Date to convert
     * @return the Instant
     */
    public static Instant toInstant(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant();
    }
    
    /**
     * Converts an Instant to a Date.
     *
     * @param instant the Instant to convert
     * @return the Date
     */
    public static Date toDate(Instant instant) {
        if (instant == null) {
            return null;
        }
        return Date.from(instant);
    }
    
    /**
     * Converts a Date to a LocalDateTime (for backward compatibility if needed).
     *
     * @param date the Date to convert
     * @return the LocalDateTime
     */
    public static LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
    
    /**
     * Converts a LocalDateTime to a Date (for backward compatibility if needed).
     *
     * @param localDateTime the LocalDateTime to convert
     * @return the Date
     */
    public static Date toDate(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }
    
    /**
     * Converts a LocalDateTime to an Instant.
     *
     * @param localDateTime the LocalDateTime to convert
     * @return the Instant
     */
    public static Instant toInstant(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.atZone(ZoneId.systemDefault()).toInstant();
    }
} 