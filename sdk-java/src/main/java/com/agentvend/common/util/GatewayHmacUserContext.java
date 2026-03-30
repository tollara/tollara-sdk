package com.agentvend.common.util;

import java.math.BigDecimal;
import java.util.List;

/**
 * Canonical suffix for gateway → agent inbound HMAC: appended after {@code payload + timestamp}.
 * Order must match gateway-service forwarding (see docs/hmac-spec.md).
 */
public final class GatewayHmacUserContext {

    private GatewayHmacUserContext() {
    }

    /**
     * Builds userContextString: userId, plan, rolesCsv, quota, subscriptionActive ("true"/"false"),
     * billingModelType, measurementType, unitLabel. Null strings become "".
     */
    public static String build(
            String userId,
            String plan,
            List<String> roles,
            BigDecimal quotaRemaining,
            boolean subscriptionActive,
            String billingModelType,
            String measurementType,
            String unitLabel) {
        StringBuilder sb = new StringBuilder();
        sb.append(userId != null ? userId : "");
        sb.append(plan != null ? plan : "");
        if (roles != null && !roles.isEmpty()) {
            sb.append(String.join(",", roles));
        }
        if (quotaRemaining != null) {
            sb.append(quotaRemaining.toString());
        }
        sb.append(Boolean.toString(subscriptionActive));
        sb.append(billingModelType != null ? billingModelType : "");
        sb.append(measurementType != null ? measurementType : "");
        sb.append(unitLabel != null ? unitLabel : "");
        return sb.toString();
    }
}
