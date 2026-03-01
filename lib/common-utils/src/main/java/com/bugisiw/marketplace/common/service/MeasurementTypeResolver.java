package com.bugisiw.marketplace.common.service;

import com.bugisiw.marketplace.common.model.billing.MeasurementType;

/**
 * Resolves MeasurementType from unitLabel for backfill and fallback when config has no measurement_type.
 * PER_TOKEN and PER_BYTE are only set when config explicitly has measurement_type (no string inference).
 */
public final class MeasurementTypeResolver {

    private MeasurementTypeResolver() {
    }

    private static final String[] TIME_KEYWORDS = {
            "hour", "minute", "second", "day", "week", "month", "year"
    };

    /**
     * Derives MeasurementType from unitLabel. Time-related keywords → PER_TIME_UNIT; otherwise → PER_REQUEST.
     * Does not infer PER_TOKEN or PER_BYTE from unitLabel; those must be set explicitly via config.
     */
    public static MeasurementType fromUnitLabel(String unitLabel) {
        if (unitLabel == null || unitLabel.isBlank()) {
            return MeasurementType.PER_REQUEST;
        }
        String lower = unitLabel.toLowerCase();
        for (String keyword : TIME_KEYWORDS) {
            if (lower.contains(keyword)) {
                return MeasurementType.PER_TIME_UNIT;
            }
        }
        return MeasurementType.PER_REQUEST;
    }

    /**
     * Returns true if unitLabel indicates time-based units (hour, minute, etc.).
     */
    public static boolean isTimeBasedUnit(String unitLabel) {
        return fromUnitLabel(unitLabel) == MeasurementType.PER_TIME_UNIT;
    }
}
