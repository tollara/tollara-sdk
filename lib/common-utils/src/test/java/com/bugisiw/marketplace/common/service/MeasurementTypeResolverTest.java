package com.bugisiw.marketplace.common.service;

import com.bugisiw.marketplace.common.model.billing.MeasurementType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeasurementTypeResolverTest {

    @Test
    void fromUnitLabel_nullOrBlank_returnsPerRequest() {
        assertEquals(MeasurementType.PER_REQUEST, MeasurementTypeResolver.fromUnitLabel(null));
        assertEquals(MeasurementType.PER_REQUEST, MeasurementTypeResolver.fromUnitLabel(""));
        assertEquals(MeasurementType.PER_REQUEST, MeasurementTypeResolver.fromUnitLabel("   "));
    }

    @Test
    void fromUnitLabel_timeKeywords_returnsPerTimeUnit() {
        assertEquals(MeasurementType.PER_TIME_UNIT, MeasurementTypeResolver.fromUnitLabel("hour"));
        assertEquals(MeasurementType.PER_TIME_UNIT, MeasurementTypeResolver.fromUnitLabel("minute"));
        assertEquals(MeasurementType.PER_TIME_UNIT, MeasurementTypeResolver.fromUnitLabel("second"));
        assertEquals(MeasurementType.PER_TIME_UNIT, MeasurementTypeResolver.fromUnitLabel("day"));
        assertEquals(MeasurementType.PER_TIME_UNIT, MeasurementTypeResolver.fromUnitLabel("week"));
        assertEquals(MeasurementType.PER_TIME_UNIT, MeasurementTypeResolver.fromUnitLabel("month"));
        assertEquals(MeasurementType.PER_TIME_UNIT, MeasurementTypeResolver.fromUnitLabel("year"));
        assertEquals(MeasurementType.PER_TIME_UNIT, MeasurementTypeResolver.fromUnitLabel("per hour"));
        assertEquals(MeasurementType.PER_TIME_UNIT, MeasurementTypeResolver.fromUnitLabel("HOUR"));
    }

    @Test
    void fromUnitLabel_nonTime_returnsPerRequest() {
        assertEquals(MeasurementType.PER_REQUEST, MeasurementTypeResolver.fromUnitLabel("request"));
        assertEquals(MeasurementType.PER_REQUEST, MeasurementTypeResolver.fromUnitLabel("token"));
        assertEquals(MeasurementType.PER_REQUEST, MeasurementTypeResolver.fromUnitLabel("byte"));
        assertEquals(MeasurementType.PER_REQUEST, MeasurementTypeResolver.fromUnitLabel("units"));
    }

    @Test
    void isTimeBasedUnit_returnsTrueForTimeKeywords() {
        assertTrue(MeasurementTypeResolver.isTimeBasedUnit("hour"));
        assertTrue(MeasurementTypeResolver.isTimeBasedUnit("month"));
    }

    @Test
    void isTimeBasedUnit_returnsFalseForNonTime() {
        assertFalse(MeasurementTypeResolver.isTimeBasedUnit("request"));
        assertFalse(MeasurementTypeResolver.isTimeBasedUnit(null));
    }
}
