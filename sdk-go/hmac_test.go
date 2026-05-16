package sdk

import (
	"net/http"
	"testing"
)

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
