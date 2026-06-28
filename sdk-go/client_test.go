package sdk

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func newTestClient(t *testing.T, handler http.Handler) *TollaraClient {
	t.Helper()
	server := httptest.NewServer(handler)
	t.Cleanup(server.Close)
	client, err := NewTollaraClient(TollaraClientOptions{
		APIURL:        server.URL,
		ServiceSecret: "secret",
	})
	if err != nil {
		t.Fatalf("NewTollaraClient: %v", err)
	}
	return client
}

func TestSendProgressUpdate_PostsWithSignature(t *testing.T) {
	var gotPath, gotSig, gotTS string
	client := newTestClient(t, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotPath = r.URL.Path
		gotSig = r.Header.Get(HeaderSignature)
		gotTS = r.Header.Get(HeaderTimestamp)
		w.WriteHeader(http.StatusOK)
	}))
	progressURL := client.UsageBaseURL + "/api/usage/progress/req-1?signature=ignored&timestamp=1700000000"
	result := client.SendProgressUpdate(progressURL, "req-1", "processing", 50, nil)
	if !result.Success {
		t.Fatalf("expected success, got %+v", result)
	}
	if result.HTTPStatus != 200 {
		t.Fatalf("expected 200, got %d", result.HTTPStatus)
	}
	if gotPath != "/api/usage/progress/req-1" {
		t.Fatalf("unexpected path: %s", gotPath)
	}
	if gotTS != "1700000000" {
		t.Fatalf("unexpected timestamp header: %s", gotTS)
	}
	if gotSig == "" {
		t.Fatal("expected signature header")
	}
}

func TestSendProgressUpdate_ReturnsFailureWhenTimestampMissing(t *testing.T) {
	client := newTestClient(t, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Fatal("should not call server")
	}))
	result := client.SendProgressUpdate(client.UsageBaseURL+"/api/usage/progress/req-1", "req-1", "stage", 0, nil)
	if result.Success || result.HTTPStatus != 0 {
		t.Fatalf("expected failure with status 0, got %+v", result)
	}
}

func TestSendProgressUpdate_HandlesMissingURLWithoutThrowing(t *testing.T) {
	client := newTestClient(t, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Fatal("should not call server")
	}))
	result := client.SendProgressUpdate("", "req-1", "processing", 25, nil)
	if result.Success {
		t.Fatal("expected failure")
	}
	if result.HTTPStatusText != "Missing or invalid callback/progress URL" {
		t.Fatalf("unexpected status text: %q", result.HTTPStatusText)
	}
}

func TestSendProgressUpdate_ReturnsHTTPStatusAndBodyOnFailure(t *testing.T) {
	client := newTestClient(t, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, "Invalid requestId: req-1", http.StatusNotFound)
	}))
	progressURL := client.UsageBaseURL + "/api/usage/progress/req-1?timestamp=1700000000"
	result := client.SendProgressUpdate(progressURL, "req-1", "processing", 25, nil)
	if result.Success || result.HTTPStatus != 404 {
		t.Fatalf("expected 404 failure, got %+v", result)
	}
	if !strings.Contains(result.ResponseBody, "Invalid requestId: req-1") {
		t.Fatalf("unexpected body: %q", result.ResponseBody)
	}
}

func TestSendCompletion_ReturnsFailureWhenTimestampMissing(t *testing.T) {
	client := newTestClient(t, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Fatal("should not call server")
	}))
	result := client.SendCompletion(client.UsageBaseURL+"/api/usage/complete/req-1", "req-1", "COMPLETED", 1, nil, nil, nil)
	if result.Success || result.HTTPStatus != 0 {
		t.Fatalf("expected failure with status 0, got %+v", result)
	}
}

const contractServiceSecret = "secret"

const validateSuccessV3Body = `{"valid":true,"serviceKeyId":"6ba7b810-9dad-11d1-80b4-00c04fd430c8","userId":"user-123","serviceId":"550e8400-e29b-41d4-a716-446655440000","serviceProductId":"7c9e6679-7425-40de-944b-e07fc1f90ae7","roles":["user"],"subscriptionStatus":"ACTIVE","billingModelType":"SUBSCRIPTION","measurementType":"PER_REQUEST","unitLabel":"request","timestamp":1700000000,"error":null,"validationSchemaVersion":3}`

const estimateSuccessV3Body = `{"sufficientCredits":true,"wouldExceedCap":false,"wouldAllow":true,"estimatedCost":0.1,"billingModelType":"SUBSCRIPTION","measurementType":"PER_REQUEST","unitLabel":"request","breakdown":{"unitsRemaining":199,"remainingSpendingCap":20,"isOverLimit":false},"estimateSchemaVersion":3,"timestamp":1700000000}`

const reportSuccessV2Body = `{"reportSchemaVersion":2,"status":"ok","warning":null,"userId":"user-1","serviceId":"550e8400-e29b-41d4-a716-446655440000","billingModelType":"SUBSCRIPTION","measurementType":"PER_REQUEST","unitLabel":"request","breakdown":{"unitsUsed":1,"unitsRemaining":99,"remainingSpendingCap":20,"totalUnitsUsedThisCycle":1,"isOverLimit":false,"isOverage":false,"isOverageAllowed":true}}`

func TestValidateServiceKey_ReturnsV3ResultWhenHmacValid(t *testing.T) {
	const ts = "1700000000"
	sig := CalculateHmac(validateSuccessV3Body+ts, contractServiceSecret)
	client := newTestClient(t, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/service-keys/validate" {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		w.Header().Set(HeaderSignature, sig)
		w.Header().Set(HeaderTimestamp, ts)
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(validateSuccessV3Body))
	}))
	result, err := client.ValidateServiceKey("bearer-token")
	if err != nil {
		t.Fatalf("ValidateServiceKey: %v", err)
	}
	if result == nil {
		t.Fatal("expected result")
	}
	if result.UserID != "user-123" {
		t.Fatalf("userId: %q", result.UserID)
	}
	if result.ServiceProductID != "7c9e6679-7425-40de-944b-e07fc1f90ae7" {
		t.Fatalf("serviceProductId: %q", result.ServiceProductID)
	}
	if result.SubscriptionStatus != "ACTIVE" {
		t.Fatalf("subscriptionStatus: %q", result.SubscriptionStatus)
	}
	if result.ValidationSchemaVersion != 3 {
		t.Fatalf("validationSchemaVersion: %d", result.ValidationSchemaVersion)
	}
	if !result.GrantsAccess() {
		t.Fatal("expected GrantsAccess true")
	}
}

func TestEstimateUsage_ReturnsV3BreakdownWhenHmacValid(t *testing.T) {
	const ts = "1700000000"
	sig := CalculateHmac(estimateSuccessV3Body+ts, contractServiceSecret)
	client := newTestClient(t, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/service-keys/estimate-usage" {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		w.Header().Set(HeaderSignature, sig)
		w.Header().Set(HeaderTimestamp, ts)
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(estimateSuccessV3Body))
	}))
	result, err := client.EstimateUsage("bearer-token", 1)
	if err != nil {
		t.Fatalf("EstimateUsage: %v", err)
	}
	if result == nil {
		t.Fatal("expected result")
	}
	if result.EstimateSchemaVersion != 3 {
		t.Fatalf("estimateSchemaVersion: %d", result.EstimateSchemaVersion)
	}
	if result.Breakdown == nil || result.Breakdown.RemainingSpendingCap == nil || *result.Breakdown.RemainingSpendingCap != 20 {
		t.Fatalf("breakdown.remainingSpendingCap missing or wrong: %+v", result.Breakdown)
	}
	if result.HTTPStatus != 200 {
		t.Fatalf("httpStatus: %d", result.HTTPStatus)
	}
}

func TestReportUsage_ReturnsV2Breakdown(t *testing.T) {
	client := newTestClient(t, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/usage/report" {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		if r.Header.Get(HeaderSignature) == "" || r.Header.Get(HeaderTimestamp) == "" {
			t.Fatal("expected signed usage report headers")
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(reportSuccessV2Body))
	}))
	result, err := client.ReportUsage("user-1", "550e8400-e29b-41d4-a716-446655440000", 1)
	if err != nil {
		t.Fatalf("ReportUsage: %v", err)
	}
	if result == nil {
		t.Fatal("expected result")
	}
	if result.ReportSchemaVersion != 2 {
		t.Fatalf("reportSchemaVersion: %d", result.ReportSchemaVersion)
	}
	if result.Breakdown == nil || result.Breakdown.UnitsRemaining == nil || *result.Breakdown.UnitsRemaining != 99 {
		t.Fatalf("breakdown.unitsRemaining missing or wrong: %+v", result.Breakdown)
	}
}
