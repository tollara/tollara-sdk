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
