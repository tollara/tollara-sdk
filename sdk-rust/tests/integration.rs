//! Integration tests for the SDK HTTP clients against a mocked Agent Hub API.
//! Run with: cargo test --features http
//! See docs/sdk-api-spec.md for API details.

#![cfg(feature = "http")]

use agentvend_agent_sdk::gateway_client;
use agentvend_agent_sdk::usage_client::{self, CompletionStatus};
use agentvend_agent_sdk::validation_client;
use agentvend_agent_sdk::calculate_hmac;
use reqwest::Client;

const AGENT_SECRET: &str = "test-agent-secret";
const AGENT_ID: &str = "550e8400-e29b-41d4-a716-446655440000";

// ---------- Validation client (Core API §2) ----------

#[tokio::test]
async fn validate_agent_key_returns_result_when_core_returns_200_with_valid_hmac() {
    let mut server = mockito::Server::new();
    let core_base = format!("{}/api/v1", server.url());

    // Use exact string so HMAC matches (no serialization reorder)
    let body_str = r#"{"valid":true,"userId":"user-123","agentId":"550e8400-e29b-41d4-a716-446655440000","plan":"basic","roles":["user"],"quotaRemaining":100,"subscriptionActive":true,"timestamp":1700000000,"error":null}"#;
    let timestamp = "1700000000";
    let canonical = format!("{}{}", body_str, timestamp);
    let signature = calculate_hmac(&canonical, AGENT_SECRET);

    let _mock = server
        .mock("POST", "/api/v1/agent-keys/validate")
        .with_status(200)
        .with_header("content-type", "application/json")
        .with_header("X-AgentVend-Signature", &signature)
        .with_header("X-AgentVend-Timestamp", timestamp)
        .with_body(&body_str)
        .create();

    let client = Client::new();
    let result = validation_client::validate_agent_key(
        &client,
        &core_base,
        "bearer-token-xyz",
        AGENT_SECRET,
        Some(AGENT_ID),
    )
    .await;

    assert!(result.is_some());
    let r = result.unwrap();
    assert_eq!(r.user_id.as_deref(), Some("user-123"));
    assert_eq!(r.agent_id.as_deref(), Some(AGENT_ID));
    assert_eq!(r.plan.as_deref(), Some("basic"));
    assert_eq!(r.roles, &["user"]);
    assert_eq!(r.quota_remaining, Some(100.0));
    assert!(r.subscription_active);
}

#[tokio::test]
async fn validate_agent_key_returns_none_when_core_returns_401() {
    let mut server = mockito::Server::new();
    let core_base = format!("{}/api/v1", server.url());

    let _mock = server
        .mock("POST", "/api/v1/agent-keys/validate")
        .with_status(401)
        .with_header("content-type", "application/json")
        .with_body(r#"{"valid":false,"error":"Invalid key"}"#)
        .create();

    let client = Client::new();
    let result = validation_client::validate_agent_key(
        &client,
        &core_base,
        "bad-key",
        AGENT_SECRET,
        Some(AGENT_ID),
    )
    .await;

    assert!(result.is_none());
}

#[tokio::test]
async fn validate_agent_key_returns_none_when_hmac_invalid() {
    let mut server = mockito::Server::new();
    let core_base = format!("{}/api/v1", server.url());

    let body_str = r#"{"valid":true,"userId":"user-123","agentId":"550e8400-e29b-41d4-a716-446655440000","plan":"basic","roles":[],"quotaRemaining":100,"subscriptionActive":true,"timestamp":1700000000,"error":null}"#;

    let _mock = server
        .mock("POST", "/api/v1/agent-keys/validate")
        .with_status(200)
        .with_header("content-type", "application/json")
        .with_header("X-AgentVend-Signature", "invalid-signature")
        .with_header("X-AgentVend-Timestamp", "1700000000")
        .with_body(body_str)
        .create();

    let client = Client::new();
    let result = validation_client::validate_agent_key(
        &client,
        &core_base,
        "bearer-token",
        AGENT_SECRET,
        Some(AGENT_ID),
    )
    .await;

    assert!(result.is_none());
}

// ---------- Usage client (Usage API §3) ----------

#[tokio::test]
async fn report_usage_sends_signed_request_and_returns_response() {
    let mut server = mockito::Server::new();
    let usage_base = server.url();

    let _mock = server
        .mock("POST", "/api/usage/report")
        .with_status(200)
        .with_header("content-type", "application/json")
        .with_body(
            r#"{"status":"ok","isOverLimit":false,"remainingRequestsPerPeriod":99}"#,
        )
        .create();

    let client = Client::new();
    let result = usage_client::report_usage_at(
        &client,
        usage_base,
        "user-1",
        "agent-1",
        1.0,
        AGENT_SECRET,
        Some(1700000000.0),
    )
    .await;

    assert!(result.is_ok());
    let r = result.unwrap();
    assert_eq!(r.status.as_deref(), Some("ok"));
    assert!(!r.is_over_limit);
    assert_eq!(r.remaining_requests_per_period, 99);
}

#[tokio::test]
async fn report_usage_errors_on_5xx() {
    let mut server = mockito::Server::new();
    let usage_base = server.url();

    let _mock = server
        .mock("POST", "/api/usage/report")
        .with_status(500)
        .with_body("Internal error")
        .create();

    let client = Client::new();
    let result = usage_client::report_usage(
        &client,
        usage_base,
        "user-1",
        "agent-1",
        1.0,
        AGENT_SECRET,
    )
    .await;

    assert!(result.is_err());
}

#[tokio::test]
async fn report_progress_posts_to_progress_url_with_signature() {
    let mut server = mockito::Server::new();
    let progress_path = "/api/usage/progress/req-123";
    let timestamp = "1700000000";
    let progress_url = format!("{}{}?signature=ignored&timestamp={}", server.url(), progress_path, timestamp);

    let _mock = server
        .mock("POST", progress_path)
        .with_status(200)
        .create();

    let client = Client::new();
    let ok = usage_client::report_progress_simple(
        &client,
        &progress_url,
        "req-123",
        "processing",
        50,
        AGENT_SECRET,
    )
    .await;

    assert!(ok);
}

#[tokio::test]
async fn report_progress_returns_false_when_url_missing_timestamp() {
    let mut server = mockito::Server::new();
    let progress_url = format!("{}/api/usage/progress/req-1", server.url());

    let client = Client::new();
    let ok = usage_client::report_progress(
        &client,
        &progress_url,
        "req-1",
        "stage",
        0,
        AGENT_SECRET,
        None,
    )
    .await;

    assert!(!ok);
}

#[tokio::test]
async fn report_completion_posts_to_callback_url_with_signature() {
    let mut server = mockito::Server::new();
    let complete_path = "/api/usage/complete/req-456";
    let timestamp = "1700000001";
    let callback_url = format!("{}{}?timestamp={}", server.url(), complete_path, timestamp);

    let _mock = server
        .mock("POST", complete_path)
        .with_status(200)
        .create();

    let client = Client::new();
    let ok = usage_client::report_completion_with_result(
        &client,
        &callback_url,
        "req-456",
        CompletionStatus::Completed,
        AGENT_SECRET,
        "done",
        1.0,
    )
    .await;

    assert!(ok);
}

#[tokio::test]
async fn report_completion_returns_false_when_url_missing_timestamp() {
    let mut server = mockito::Server::new();
    let callback_url = format!("{}/api/usage/complete/req-1", server.url());

    let client = Client::new();
    let ok = usage_client::report_completion(
        &client,
        &callback_url,
        "req-1",
        CompletionStatus::Failed,
        AGENT_SECRET,
        0.0,
    )
    .await;

    assert!(!ok);
}

#[tokio::test]
async fn get_request_status_sends_bearer() {
    let mut server = mockito::Server::new();
    let gw = server.url();
    let _m = server
        .mock("GET", "/api/requests/j1/status")
        .match_header("authorization", "Bearer my-key")
        .with_status(200)
        .with_body(r#"{"state":"PENDING"}"#)
        .create();

    let client = Client::new();
    let (ok, status, body) = gateway_client::get_request_status(
        &client,
        &gw,
        "/api",
        "j1",
        "my-key",
    )
    .await
    .expect("request");
    assert!(ok);
    assert_eq!(status, 200);
    assert!(body.contains("PENDING"));
}
