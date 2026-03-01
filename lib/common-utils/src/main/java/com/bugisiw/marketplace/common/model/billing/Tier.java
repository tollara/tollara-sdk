package com.bugisiw.marketplace.common.model.billing;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tier {
    private BigDecimal threshold;
    private BigDecimal price;
    private BigDecimal maxUnits;
} 