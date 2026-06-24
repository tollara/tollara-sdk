//! Integration tests for the SDK HTTP clients against a mocked Agent Hub API.
//! Run with: cargo test --features http
//! See docs/sdk-api-spec.md for API details.

#![cfg(feature = "http")]

use tollara_service_sdk::tollara_client::{TollaraClient, TollaraClientConfig};
use tollara_service_sdk::gateway_client;
use tollara_service_sdk::usage_client::{self, CompletionStatus};
use tollara_service_sdk::validation_client;
use tollara_service_sdk::calculate_hmac;
use reqwest::Client;

const AGENT_SECRET: &str = "test-agent-secret";
const AGENT_ID: &str = "550e8400-e29b-41d4-a716-446655440000";
const AGENT_KEY: &str = "k";

// ---------- Validation client (Core API §2) ----------

#[tokio::test]
async fn validate_service_key_returns_result_when_core_returns_200_with_valid_hmac() {
    let mut server = mockito::Server::new();
    let core_base = format!("{}/api/v1", server.url());

    // Use exact string so HMAC matches (no serialization reorder)
    let body_str = r#"{"valid":true,"userId":"user-123","serviceId":"550e8400-e29b-41d4-a716-446655440000","plan":"basic","roles":["user"],"quotaRemaining":100,"subscriptionActive":true,"timestamp":1700000000,"error":null}"#;
    let timestamp = "1700000000";
    let canonical = format!("{}{}", body_str, timestamp);
    let signature = calculate_hmac(&canonical, AGENT_SECRET);

    let _mock = server
        .mock("POST", "/api/v1/service-keys/validate")
        .with_status(200)
        .with_header("content-type", "application/json")
        .with_header("X-Tollara-Signature", &signature)
        .with_header("X-Tollara-Timestamp", timestamp)
        .with_body(&body_str)
        .create();

    let client = Client::new();
    let result = validation_client::validate_service_key(
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
    assert_eq!(r.service_id.as_deref(), Some(AGENT_ID));
    assert_eq!(r.plan.as_deref(), Some("basic"));
    assert_eq!(r.roles, &["user"]);
    assert_eq!(r.quota_remaining, Some(100.0));
    assert!(r.subscription_active);
}

#[tokio::test]
async fn validate_service_key_returns_none_when_core_returns_401() {
    let mut server = mockito::Server::new();
    let core_base = format!("{}/api/v1", server.url());

    let _mock = server
        .mock("POST", "/api/v1/service-keys/validate")
        .with_status(401)
        .with_header("content-type", "application/json")
        .with_body(r#"{"valid":false,"error":"Invalid key"}"#)
        .create();

    let client = Client::new();
    let result = validation_client::validate_service_key(
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
async fn validate_service_key_returns_none_when_hmac_invalid() {
    let mut server = mockito::Server::new();
    let core_base = format!("{}/api/v1", server.url());

    let body_str = r#"{"valid":true,"userId":"user-123","serviceId":"550e8400-e29b-41d4-a716-446655440000","plan":"basic","roles":[],"quotaRemaining":100,"subscriptionActive":true,"timestamp":1700000000,"error":null}"#;

    let _mock = server
        .mock("POST", "/api/v1/service-keys/validate")
        .with_status(200)
        .with_header("content-type", "application/json")
        .with_header("X-Tollara-Signature", "invalid-signature")
        .with_header("X-Tollara-Timestamp", "1700000000")
        .with_body(body_str)
        .create();

    let client = Client::new();
    let result = validation_client::validate_service_key(
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
        None,
    )
    .await;

    assert!(result.is_ok());
    let r = result.unwrap();
    assert_eq!(r.status.as_deref(), Some("ok"));
    assert!(!r.is_over_limit);
    assert_eq!(r.remaining_requests_per_period, 99);
}

#[tokio::test]
async fn report_usage_respects_custom_usage_path_prefix() {
    let mut server = mockito::Server::new();
    let usage_base = server.url();

    let _mock = server
        .mock("POST", "/usage/api/v1/report")
        .with_status(200)
        .with_header("content-type", "application/json")
        .with_body(
            r#"{"status":"ok","isOverLimit":false,"remainingRequestsPerPeriod":1}"#,
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
        Some("/usage/api/v1"),
    )
    .await;

    assert!(result.is_ok());
}

#[tokio::test]
async fn tollara_client_get_request_status_uses_default_gateway_prefix() {
    let mut server = mockito::Server::new();
    let base = server.url();

    let _mock = server
        .mock("GET", "/api/requests/job-1/status")
        .with_status(200)
        .with_body(r#"{"state":"OK"}"#)
        .create();

    let http = Client::new();
    let av = TollaraClient::try_new(TollaraClientConfig {
        api_url: Some(base),
        service_id: Some(AGENT_ID.to_string()),
        service_secret: Some(AGENT_SECRET.to_string()),
        http_client: Some(http),
        ..Default::default()
    })
    .unwrap();

    let (ok, code, body) = av.get_request_status("job-1", AGENT_KEY).await.unwrap();
    assert!(ok);
    assert_eq!(code, 200);
    assert!(body.contains("OK"));
}

#[tokio::test]
async fn tollara_client_invoke_service_uses_service_path() {
    let mut server = mockito::Server::new();
    let base = server.url();

    let _mock = server
        .mock("POST", "/api/service/svc-1/endpoint/ep-1/invoke")
        .match_header("authorization", "Bearer my-key")
        .match_body(r#"{"x":1}"#)
        .with_status(200)
        .with_body(r#"{"ok":true}"#)
        .create();

    let http = Client::new();
    let av = TollaraClient::try_new(TollaraClientConfig {
        api_url: Some(base),
        service_id: Some(AGENT_ID.to_string()),
        service_secret: Some(AGENT_SECRET.to_string()),
        http_client: Some(http),
        ..Default::default()
    })
    .unwrap();

    let (status, body) = av
        .invoke_service(
            gateway_client::GatewayHttpMethod::Post,
            "svc-1",
            "ep-1",
            "my-key",
            Some(r#"{"x":1}"#),
            false,
        )
        .await
        .unwrap();
    assert_eq!(status, 200);
    assert!(body.contains("ok"));
}

#[tokio::test]
async fn tollara_client_report_usage_uses_default_usage_prefix() {
    let mut server = mockito::Server::new();
    let base = server.url();

    let _mock = server
        .mock("POST", "/api/usage/report")
        .with_status(200)
        .with_header("content-type", "application/json")
        .with_body(
            r#"{"status":"ok","isOverLimit":false,"remainingRequestsPerPeriod":1}"#,
        )
        .create();

    let http = Client::new();
    let av = TollaraClient::try_new(TollaraClientConfig {
        api_url: Some(base),
        service_id: Some(AGENT_ID.to_string()),
        service_secret: Some(AGENT_SECRET.to_string()),
        http_client: Some(http),
        ..Default::default()
    })
    .unwrap();

    let r = av.report_usage("user-1", AGENT_ID, 1.0).await.unwrap();
    assert_eq!(r.status.as_deref(), Some("ok"));
}

#[tokio::test]
async fn tollara_client_custom_usage_path_prefix() {
    let mut server = mockito::Server::new();
    let base = server.url();

    let _mock = server
        .mock("POST", "/usage/api/v1/report")
        .with_status(200)
        .with_header("content-type", "application/json")
        .with_body(
            r#"{"status":"ok","isOverLimit":false,"remainingRequestsPerPeriod":1}"#,
        )
        .create();

    let http = Client::new();
    let av = TollaraClient::try_new(TollaraClientConfig {
        api_url: Some(base),
        service_id: Some(AGENT_ID.to_string()),
        service_secret: Some(AGENT_SECRET.to_string()),
        usage_path_prefix: Some("/usage/api/v1".to_string()),
        http_client: Some(http),
        ..Default::default()
    })
    .unwrap();

    let r = av.report_usage("user-1", AGENT_ID, 1.0).await.unwrap();
    assert_eq!(r.status.as_deref(), Some("ok"));
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
    let result = usage_client::report_progress(
        &client,
        &progress_url,
        "req-123",
        "processing",
        50,
        AGENT_SECRET,
        None,
    )
    .await;

    assert!(result.success);
    assert_eq!(result.http_status, 200);
}

#[tokio::test]
async fn report_progress_returns_failure_when_url_missing_timestamp() {
    let mut server = mockito::Server::new();
    let progress_url = format!("{}/api/usage/progress/req-1", server.url());

    let client = Client::new();
    let result = usage_client::report_progress(
        &client,
        &progress_url,
        "req-1",
        "stage",
        0,
        AGENT_SECRET,
        None,
    )
    .await;

    assert!(!result.success);
    assert_eq!(result.http_status, 0);
}

#[tokio::test]
async fn report_progress_handles_missing_url_without_throwing() {
    let client = Client::new();
    let result = usage_client::report_progress(
        &client,
        "",
        "req-1",
        "processing",
        25,
        AGENT_SECRET,
        None,
    )
    .await;

    assert!(!result.success);
    assert_eq!(result.http_status_text, "Missing or invalid callback/progress URL");
}

#[tokio::test]
async fn report_progress_returns_http_status_and_body_on_failure() {
    let mut server = mockito::Server::new();
    let progress_path = "/api/usage/progress/req-1";
    let progress_url = format!("{}{}?timestamp=1700000000", server.url(), progress_path);

    let _mock = server
        .mock("POST", progress_path)
        .with_status(404)
        .with_body("Invalid requestId: req-1")
        .create();

    let client = Client::new();
    let result = usage_client::report_progress(
        &client,
        &progress_url,
        "req-1",
        "processing",
        25,
        AGENT_SECRET,
        None,
    )
    .await;

    assert!(!result.success);
    assert_eq!(result.http_status, 404);
    assert_eq!(result.response_body.as_deref(), Some("Invalid requestId: req-1"));
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
    let result = usage_client::report_completion(
        &client,
        &callback_url,
        "req-456",
        CompletionStatus::Completed,
        AGENT_SECRET,
        1.0,
        Some("done"),
        None,
        None,
    )
    .await;

    assert!(result.success);
}

#[tokio::test]
async fn report_completion_returns_failure_when_url_missing_timestamp() {
    let mut server = mockito::Server::new();
    let callback_url = format!("{}/api/usage/complete/req-1", server.url());

    let client = Client::new();
    let result = usage_client::report_completion(
        &client,
        &callback_url,
        "req-1",
        CompletionStatus::Failed,
        AGENT_SECRET,
        0.0,
        None,
        None,
        None,
    )
    .await;

    assert!(!result.success);
    assert_eq!(result.http_status, 0);
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
