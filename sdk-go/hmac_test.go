package sdk

import (
	"net/http"
	"testing"
)

func TestBuildGatewayUserContextStringV3_allFieldsPresent_goldenString(t *testing.T) {
	ctx := BuildGatewayUserContextStringV3(
		"sub-ext-id", "prod-uuid-1", []string{"roleA", "roleB"},
		"ACTIVE", "SUBSCRIPTION", "PER_REQUEST", "request",
	)
	want := "3sub-ext-idprod-uuid-1roleA,roleBACTIVESUBSCRIPTIONPER_REQUESTrequest"
	if ctx != want {
		t.Fatalf("got %q want %q", ctx, want)
	}
}

func TestBuildGatewayUserContextStringV3_emptyRoles_goldenString(t *testing.T) {
	ctx := BuildGatewayUserContextStringV3("user-1", "prod-1", []string{}, "TRIAL", "", "", "")
	want := "3user-1prod-1TRIAL"
	if ctx != want {
		t.Fatalf("got %q want %q", ctx, want)
	}
}

func TestBuildGatewayUserContextStringV3_billingFieldsAbsent_goldenString(t *testing.T) {
	ctx := BuildGatewayUserContextStringV3("owner-id", "", nil, "ACTIVE", "", "", "")
	want := "3owner-idACTIVE"
	if ctx != want {
		t.Fatalf("got %q want %q", ctx, want)
	}
}

func TestBuildGatewayUserContextStringV3_nonAccessStatus_goldenString(t *testing.T) {
	ctx := BuildGatewayUserContextStringV3(
		"user-x", "prod-x", []string{"r1"},
		"EXPIRED", "PREPAID", "PER_REQUEST", "request",
	)
	want := "3user-xprod-xr1EXPIREDPREPAIDPER_REQUESTrequest"
	if ctx != want {
		t.Fatalf("got %q want %q", ctx, want)
	}
}

func TestGrantAccess(t *testing.T) {
	if !GrantAccess("ACTIVE") || !GrantAccess("TRIAL") || !GrantAccess("CANCELLING_PENDING") {
		t.Fatal("expected eligible statuses to grant access")
	}
	if GrantAccess("EXPIRED") || GrantAccess("") {
		t.Fatal("expected non-eligible statuses to deny access")
	}
}

func TestVerifyInboundHMAC_v3(t *testing.T) {
	secret := "my-agent-secret"
	payload := ""
	ts := "1700000000"
	userCtx := BuildGatewayUserContextStringV3(
		"user1", "prod-1", []string{"role1", "role2"},
		"ACTIVE", "SUBSCRIPTION", "PER_REQUEST", "request",
	)
	sig := CalculateHmac(payload+ts+userCtx, secret)
	req := &InboundHmacRequest{
		Signature:          sig,
		Timestamp:          ts,
		Payload:            payload,
		UserID:             "user1",
		ServiceProductID:   "prod-1",
		Roles:              []string{"role1", "role2"},
		SubscriptionStatus: "ACTIVE",
		BillingModelType:   "SUBSCRIPTION",
		MeasurementType:    "PER_REQUEST",
		UnitLabel:          "request",
		SigningVersion:     SigningVersionV3,
	}
	if !VerifyInboundHMAC(secret, req) {
		t.Fatal("expected valid v3 HMAC")
	}
}

func TestVerifyInboundHMACFromHeaders_v3(t *testing.T) {
	secret := "my-agent-secret"
	payload := ""
	ts := "1700000000"
	userCtx := BuildGatewayUserContextStringV3(
		"user1", "prod-1", []string{"role1", "role2"},
		"ACTIVE", "SUBSCRIPTION", "PER_REQUEST", "request",
	)
	sig := CalculateHmac(payload+ts+userCtx, secret)
	h := http.Header{}
	h.Set("x-tollara-signature", sig)
	h.Set("x-tollara-timestamp", ts)
	h.Set("x-tollara-signing-version", "3")
	h.Set("x-tollara-user-id", "user1")
	h.Set("x-tollara-service-product-id", "prod-1")
	h.Set("x-tollara-roles", "role1,role2")
	h.Set("x-tollara-subscription-status", "ACTIVE")
	h.Set("x-tollara-billing-model", "SUBSCRIPTION")
	h.Set("x-tollara-measurement-type", "PER_REQUEST")
	h.Set("x-tollara-unit-label", "request")
	ctx, ok := VerifyInboundHMACFromHeadersAndGetUserContext(secret, h, payload)
	if !ok {
		t.Fatal("expected ok")
	}
	if ctx.ServiceProductID != "prod-1" || ctx.SubscriptionStatus != "ACTIVE" {
		t.Fatalf("unexpected ctx: %+v", ctx)
	}
}

func TestVerifyInboundHMAC_extendedVector(t *testing.T) {
	secret := "my-agent-secret"
	payload := ""
	ts := "1700000000"
	userCtx := BuildGatewayUserContextString("user1", "plan1", []string{"role1", "role2"}, "10", false, "", "", "")
	sig := CalculateHmac(payload+ts+userCtx, secret)
	req := &InboundHmacRequest{
		Signature:          sig,
		Timestamp:          ts,
		Payload:            payload,
		UserID:             "user1",
		Plan:               "plan1",
		Roles:              []string{"role1", "role2"},
		QuotaRemaining:     "10",
		SubscriptionActive: false,
	}
	if !VerifyInboundHMAC(secret, req) {
		t.Fatal("expected valid HMAC")
	}
}

func TestVerifyInboundHMACFromHeaders(t *testing.T) {
	secret := "my-agent-secret"
	payload := ""
	ts := "1700000000"
	userCtx := BuildGatewayUserContextString("user1", "plan1", []string{"role1", "role2"}, "10", false, "", "", "")
	sig := CalculateHmac(payload+ts+userCtx, secret)
	h := http.Header{}
	h.Set("x-tollara-signature", sig)
	h.Set("x-tollara-timestamp", ts)
	h.Set("x-tollara-user-id", "user1")
	h.Set("x-tollara-plan", "plan1")
	h.Set("x-tollara-roles", "role1,role2")
	h.Set("x-tollara-quota-remaining", "10")
	h.Set("x-tollara-subscription-active", "false")
	if !VerifyInboundHMACFromHeaders(secret, h, payload) {
		t.Fatal("expected valid HMAC from headers")
	}
}

func TestVerifyInboundHMACFromHeadersAndGetUserContext_ok(t *testing.T) {
	secret := "my-agent-secret"
	payload := ""
	ts := "1700000000"
	userCtx := BuildGatewayUserContextString("user1", "plan1", []string{"role1", "role2"}, "10", false, "", "", "")
	sig := CalculateHmac(payload+ts+userCtx, secret)
	h := http.Header{}
	h.Set("x-tollara-signature", sig)
	h.Set("x-tollara-timestamp", ts)
	h.Set("x-tollara-user-id", "user1")
	h.Set("x-tollara-plan", "plan1")
	h.Set("x-tollara-roles", "role1,role2")
	h.Set("x-tollara-quota-remaining", "10")
	h.Set("x-tollara-subscription-active", "false")
	ctx, ok := VerifyInboundHMACFromHeadersAndGetUserContext(secret, h, payload)
	if !ok {
		t.Fatal("expected ok")
	}
	if ctx.UserID != "user1" || ctx.Plan != "plan1" {
		t.Fatalf("unexpected ctx: %+v", ctx)
	}
}

func TestVerifyInboundHMACFromHeadersAndGetUserContext_invalid(t *testing.T) {
	h := http.Header{}
	h.Set("x-tollara-signature", "bad")
	h.Set("x-tollara-timestamp", "1700000000")
	h.Set("x-tollara-user-id", "user1")
	_, ok := VerifyInboundHMACFromHeadersAndGetUserContext("my-agent-secret", h, "")
	if ok {
		t.Fatal("expected not ok")
	}
}

func TestSubscriberWithBilling(t *testing.T) {
	secret := "test-agent-secret"
	payload := ""
	ts := "1710000000"
	userCtx := BuildGatewayUserContextString("sub-user", "basic", []string{"roleA", "roleB"}, "50", true, "SUBSCRIPTION", "PER_REQUEST", "request")
	sig := CalculateHmac(payload+ts+userCtx, secret)
	req := &InboundHmacRequest{
		Signature:          sig,
		Timestamp:          ts,
		Payload:            payload,
		UserID:             "sub-user",
		Plan:               "basic",
		Roles:              []string{"roleA", "roleB"},
		QuotaRemaining:     "50",
		SubscriptionActive:   true,
		BillingModelType:     "SUBSCRIPTION",
		MeasurementType:      "PER_REQUEST",
		UnitLabel:            "request",
	}
	if !VerifyInboundHMAC(secret, req) {
		t.Fatal("expected valid HMAC")
	}
}
