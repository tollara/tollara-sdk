package sdk

import (
	"net/http"
	"testing"
)

func TestVerifyInboundHMAC_hmacSpecVector(t *testing.T) {
	secret := "my-agent-secret"
	payload := ""
	ts := "1700000000"
	userCtx := "user1plan1role1,role210"
	sig := CalculateHmac(payload+ts+userCtx, secret)
	req := &InboundHmacRequest{
		Signature:      sig,
		Timestamp:      ts,
		Payload:        payload,
		UserID:         "user1",
		Plan:           "plan1",
		Roles:          []string{"role1", "role2"},
		QuotaRemaining: "10",
	}
	if !VerifyInboundHMAC(secret, req) {
		t.Fatal("expected valid HMAC")
	}
}

func TestVerifyInboundHMACFromHeaders(t *testing.T) {
	secret := "my-agent-secret"
	payload := ""
	ts := "1700000000"
	sig := CalculateHmac(payload+ts+"user1plan1role1,role210", secret)
	h := http.Header{}
	h.Set("x-agentvend-signature", sig)
	h.Set("x-agentvend-timestamp", ts)
	h.Set("x-agentvend-user-id", "user1")
	h.Set("x-agentvend-plan", "plan1")
	h.Set("x-agentvend-roles", "role1,role2")
	h.Set("x-agentvend-quota-remaining", "10")
	if !VerifyInboundHMACFromHeaders(secret, h, payload) {
		t.Fatal("expected valid HMAC from headers")
	}
}
