package sdk

import (
	"net/http"
	"testing"
)

func TestOutcomeFromValidateResponse_Unsigned401InvalidKey(t *testing.T) {
	outcome := outcomeFromValidateResponse(
		`{"valid":false,"error":"Invalid service key"}`,
		http.Header{},
		401,
		"secret",
		"",
	)
	if outcome.OK {
		t.Fatal("expected failure")
	}
	if outcome.Failure == nil || outcome.Failure.Code != ValidationFailureInvalidKey {
		t.Fatalf("expected INVALID_KEY, got %+v", outcome.Failure)
	}
	if outcome.Failure.Message != "Invalid service key" {
		t.Fatalf("message: %q", outcome.Failure.Message)
	}
	if outcome.Failure.HTTPStatus != 401 {
		t.Fatalf("httpStatus: %d", outcome.Failure.HTTPStatus)
	}
}

func TestOutcomeFromValidateResponse_Unsigned401WithoutJSONBody(t *testing.T) {
	outcome := outcomeFromValidateResponse("unauthorized", http.Header{}, 401, "secret", "")
	if outcome.OK || outcome.Failure == nil || outcome.Failure.Code != ValidationFailureHTTPError {
		t.Fatalf("expected HTTP_ERROR, got %+v", outcome)
	}
}

func TestOutcomeFromValidateResponse_500WithValidFalse(t *testing.T) {
	outcome := outcomeFromValidateResponse(
		`{"valid":false,"error":"Internal server error"}`,
		http.Header{},
		500,
		"secret",
		"",
	)
	if outcome.Failure == nil || outcome.Failure.Code != ValidationFailureHTTPError {
		t.Fatalf("expected HTTP_ERROR, got %+v", outcome.Failure)
	}
}

func TestValidateServiceKeyWithOutcome_MissingKey(t *testing.T) {
	client, err := NewTollaraClient(TollaraClientOptions{ServiceSecret: "secret"})
	if err != nil {
		t.Fatalf("NewTollaraClient: %v", err)
	}
	outcome := client.ValidateServiceKeyWithOutcome("   ")
	if outcome.OK || outcome.Failure == nil || outcome.Failure.Code != ValidationFailureMissingKey {
		t.Fatalf("expected MISSING_KEY, got %+v", outcome)
	}
}

func TestValidateServiceKeyWithOutcome_Unsigned401InvalidKey(t *testing.T) {
	client := newTestClient(t, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusUnauthorized)
		_, _ = w.Write([]byte(`{"valid":false,"error":"Invalid service key"}`))
	}))
	outcome := client.ValidateServiceKeyWithOutcome("bad-key")
	if outcome.OK || outcome.Failure == nil || outcome.Failure.Code != ValidationFailureInvalidKey {
		t.Fatalf("expected INVALID_KEY, got %+v", outcome)
	}
	if outcome.Failure.Message != "Invalid service key" {
		t.Fatalf("message: %q", outcome.Failure.Message)
	}
}

func TestValidateServiceKeyWithOutcome_Unsigned401HTTPError(t *testing.T) {
	client := newTestClient(t, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
		_, _ = w.Write([]byte("unauthorized"))
	}))
	outcome := client.ValidateServiceKeyWithOutcome("bad-key")
	if outcome.Failure == nil || outcome.Failure.Code != ValidationFailureHTTPError {
		t.Fatalf("expected HTTP_ERROR, got %+v", outcome.Failure)
	}
}

func TestValidateServiceKey_ReturnsNilOnUnsigned401InvalidKey(t *testing.T) {
	client := newTestClient(t, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
		_, _ = w.Write([]byte(`{"valid":false,"error":"Invalid service key"}`))
	}))
	result, err := client.ValidateServiceKey("bad-key")
	if err != nil {
		t.Fatalf("ValidateServiceKey err: %v", err)
	}
	if result != nil {
		t.Fatal("expected nil result for invalid key")
	}
}
