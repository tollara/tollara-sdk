//! Client for the Usage API: report usage, progress, completion (see docs/sdk-api-spec.md §3).

use crate::hmac::calculate_hmac_with_timestamp;
use serde::Deserialize;
use serde::Serialize;

/// Response from the report-usage endpoint.
#[derive(Debug, Clone)]
pub struct UsageReportResponse {
    pub status: Option<String>,
    pub is_over_limit: bool,
    pub remaining_requests_per_period: i64,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct UsageReportResponseJson {
    status: Option<String>,
    is_over_limit: Option<bool>,
    remaining_requests_per_period: Option<i64>,
}

/// Parses `?key=value&...` and returns (base_url, timestamp value if present).
fn parse_timestamp_from_url(url: &str) -> (String, Option<String>) {
    let (base, query) = match url.split_once('?') {
        Some((b, q)) => (b.to_string(), q),
        None => return (url.to_string(), None),
    };
    for pair in query.split('&') {
        let (k, v) = match pair.split_once('=') {
            Some(x) => x,
            None => continue,
        };
        if k == "timestamp" {
            return (base, Some(v.to_string()));
        }
    }
    (base, None)
}

/// Reports usage to the usage service. Returns error on non-2xx or parse failure.
pub async fn report_usage(
    client: &reqwest::Client,
    usage_base_url: &str,
    user_id: &str,
    agent_id: &str,
    units_used: f64,
    agent_secret: &str,
    timestamp_secs: Option<f64>,
) -> Result<UsageReportResponse, Box<dyn std::error::Error + Send + Sync>> {
    use std::time::{SystemTime, UNIX_EPOCH};
    let ts_ms = timestamp_secs
        .map(|t| (t * 1000.0) as i64)
        .unwrap_or_else(|| {
            SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap()
                .as_millis() as i64
        });
    let body = serde_json::json!({
        "userId": user_id,
        "agentId": agent_id,
        "unitsUsed": units_used,
        "timestamp": ts_ms
    });
    let body_str = body.to_string();
    let ts_str = ts_ms.to_string();
    let signature = calculate_hmac_with_timestamp(&body_str, &ts_str, agent_secret);
    let url = format!("{}/api/usage/report", usage_base_url.trim_end_matches('/'));
    let resp = client
        .post(&url)
        .json(&body)
        .header("X-AgentVend-Signature", &signature)
        .header("X-AgentVend-Timestamp", &ts_str)
        .send()
        .await?;
    resp.error_for_status_ref()?;
    let data: UsageReportResponseJson = resp.json().await?;
    Ok(UsageReportResponse {
        status: data.status,
        is_over_limit: data.is_over_limit.unwrap_or(false),
        remaining_requests_per_period: data.remaining_requests_per_period.unwrap_or(0),
    })
}

/// Sends a progress update. Returns `false` if URL has no timestamp or request fails.
pub async fn report_progress(
    client: &reqwest::Client,
    progress_url: &str,
    _request_id: &str,
    stage: &str,
    percentage_complete: i32,
    agent_secret: &str,
    error_message: Option<&str>,
) -> bool {
    let (base_url, timestamp) = parse_timestamp_from_url(progress_url);
    let timestamp = match timestamp {
        Some(t) => t,
        None => return false,
    };
    let ts_secs = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap()
        .as_secs() as i64;
    let mut body = serde_json::json!({
        "stage": stage,
        "percentageComplete": percentage_complete,
        "timestamp": ts_secs,
    });
    if let Some(msg) = error_message {
        body["errorMessage"] = serde_json::Value::String(msg.to_string());
    }
    let body_str = body.to_string();
    let signature = calculate_hmac_with_timestamp(&body_str, &timestamp, agent_secret);
    let resp = client
        .post(&base_url)
        .json(&body)
        .header("X-AgentVend-Signature", signature)
        .header("X-AgentVend-Timestamp", &timestamp)
        .send()
        .await;
    match resp {
        Ok(r) => r.status().is_success(),
        Err(_) => false,
    }
}

/// Sends a completion notification. Returns `false` if URL has no timestamp or request fails.
pub async fn report_completion(
    client: &reqwest::Client,
    callback_url: &str,
    _request_id: &str,
    status: &str,
    agent_secret: &str,
    result: Option<&str>,
    result_url: Option<&str>,
    content_type: Option<&str>,
    units: Option<f64>,
) -> bool {
    let (base_url, timestamp) = parse_timestamp_from_url(callback_url);
    let timestamp = match timestamp {
        Some(t) => t,
        None => return false,
    };
    let ts_secs = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap()
        .as_secs() as i64;
    let mut body = serde_json::json!({
        "status": status,
        "timestamp": ts_secs,
        "units": units.unwrap_or(0.0),
    });
    if let Some(r) = result {
        body["result"] = serde_json::Value::String(r.to_string());
    }
    if let Some(u) = result_url {
        body["resultUrl"] = serde_json::Value::String(u.to_string());
    }
    if let Some(c) = content_type {
        body["contentType"] = serde_json::Value::String(c.to_string());
    }
    let body_str = body.to_string();
    let signature = calculate_hmac_with_timestamp(&body_str, &timestamp, agent_secret);
    let resp = client
        .post(&base_url)
        .json(&body)
        .header("X-AgentVend-Signature", signature)
        .header("X-AgentVend-Timestamp", &timestamp)
        .send()
        .await;
    match resp {
        Ok(r) => r.status().is_success(),
        Err(_) => false,
    }
}

