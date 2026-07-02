package com.tollara.common.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GatewayHmacUserContextTest {

    @Test
    void buildV3_allFieldsPresent_goldenString() {
        String ctx = GatewayHmacUserContext.buildV3(
                "sub-ext-id",
                "prod-uuid-1",
                List.of("roleA", "roleB"),
                "ACTIVE",
                "SUBSCRIPTION",
                "PER_REQUEST",
                "request");
        assertEquals("3sub-ext-idprod-uuid-1roleA,roleBACTIVESUBSCRIPTIONPER_REQUESTrequest", ctx);
    }

    @Test
    void buildV3_emptyRoles_goldenString() {
        String ctx = GatewayHmacUserContext.buildV3(
                "user-1",
                "prod-1",
                List.of(),
                "TRIAL",
                null,
                null,
                null);
        assertEquals("3user-1prod-1TRIAL", ctx);
    }

    @Test
    void buildV3_billingFieldsAbsent_goldenString() {
        String ctx = GatewayHmacUserContext.buildV3(
                "owner-id",
                "",
                null,
                "ACTIVE",
                null,
                null,
                null);
        assertEquals("3owner-idACTIVE", ctx);
    }

    @Test
    void buildV3_nonAccessStatus_goldenString() {
        String ctx = GatewayHmacUserContext.buildV3(
                "user-x",
                "prod-x",
                List.of("r1"),
                "EXPIRED",
                "PREPAID",
                "PER_REQUEST",
                "request");
        assertEquals("3user-xprod-xr1EXPIREDPREPAIDPER_REQUESTrequest", ctx);
    }

    @Test
    void build_v2_ownerNoRoles_goldenString() {
        String ctx = GatewayHmacUserContext.buildV2(
                "user-1",
                "owner",
                List.of(),
                true,
                null,
                null,
                null);
        assertEquals("2user-1ownertrue", ctx);
    }

    @Test
    void build_v2_subscriberWithRolesAndBillingFields_goldenString() {
        String ctx = GatewayHmacUserContext.buildV2(
                "sub-ext-id",
                "pro",
                List.of("roleA", "roleB"),
                false,
                "SUBSCRIPTION",
                "PER_REQUEST",
                "request");
        assertEquals("2sub-ext-idproroleA,roleBfalseSUBSCRIPTIONPER_REQUESTrequest", ctx);
    }

    @Test
    void build_legacy_v1_withQuota_goldenString() {
        String ctx = GatewayHmacUserContext.build(
                "a",
                "b",
                List.of("x"),
                new BigDecimal("5"),
                false,
                "S",
                "M",
                "U");
        assertEquals("abx5falseSMU", ctx);
    }
}
