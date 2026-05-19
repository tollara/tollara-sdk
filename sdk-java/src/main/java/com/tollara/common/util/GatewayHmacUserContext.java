package com.tollara.common.util;

import java.math.BigDecimal;
import java.util.List;

/**
 * Canonical suffix for gateway → agent inbound HMAC: appended after {@code payload + timestamp}.
 * V1 includes a quota segment; v2 uses a leading {@code "2"} and no quota (see docs/sdk-api-spec.md §4).
 */
public final class GatewayHmacUserContext {

    private GatewayHmacUserContext() {
    }

    /**
     * HMAC user-context v2: literal {@code "2"} then userId, plan, roles (comma-joined if any),
     * {@code Boolean.toString(subscriptionActive)}, billingModelType, measurementType, unitLabel. Null strings become "".
     */
    public static String buildV2(
            String userId,
            String plan,
            List<String> roles,
            boolean subscriptionActive,
            String billingModelType,
            String measurementType,
            String unitLabel) {
        StringBuilder sb = new StringBuilder();
        sb.append("2");
        sb.append(userId != null ? userId : "");
        sb.append(plan != null ? plan : "");
        if (roles != null && !roles.isEmpty()) {
            sb.append(String.join(",", roles));
        }
        sb.append(Boolean.toString(subscriptionActive));
        sb.append(billingModelType != null ? billingModelType : "");
        sb.append(measurementType != null ? measurementType : "");
        sb.append(unitLabel != null ? unitLabel : "");
        return sb.toString();
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
