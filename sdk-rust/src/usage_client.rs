//! Client for the Usage API: report usage, progress, completion (see docs/sdk-api-spec.md §3).

use crate::headers;
use crate::hmac::calculate_hmac_with_timestamp;
use serde::Deserialize;
use serde::Serialize;

/// Completion status for async completion POST (sdk-api-spec §3.3).
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum CompletionStatus {
    Completed,
    Failed,
}

impl CompletionStatus {
    #[must_use]
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Completed => "COMPLETED",
            Self::Failed => "FAILED",
        }
    }
}

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

/// Default path segment before `/report` (matches Java `AgentVendUrls.DEFAULT_USAGE_PATH_PREFIX`).
pub const DEFAULT_USAGE_PATH_PREFIX: &str = "/api/usage";

/// Builds `{base}{prefix}/report` with normalized slashes.
#[must_use]
pub fn build_usage_report_url(usage_base_url: &str, usage_path_prefix: Option<&str>) -> String {
    let base = usage_base_url.trim_end_matches('/');
    let mut p = usage_path_prefix.unwrap_or(DEFAULT_USAGE_PATH_PREFIX).trim();
    if p.is_empty() {
        p = DEFAULT_USAGE_PATH_PREFIX;
    }
    let mut prefix = if p.starts_with('/') {
        p.to_string()
    } else {
        format!("/{p}")
    };
    while prefix.ends_with('/') {
        prefix.pop();
    }
    format!("{base}{prefix}/report")
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

/// Reports usage with the current time as timestamp.
pub async fn report_usage(
    client: &reqwest::Client,
    usage_base_url: &str,
    user_id: &str,
    service_id: &str,
    units_used: f64,
    service_secret: &str,
) -> Result<UsageReportResponse, Box<dyn std::error::Error + Send + Sync>> {
    report_usage_at(
        client,
        usage_base_url,
        user_id,
        service_id,
        units_used,
        service_secret,
        None,
        None,
    )
    .await
}

/// Reports usage with an optional explicit timestamp (epoch seconds).
pub async fn report_usage_at(
    client: &reqwest::Client,
    usage_base_url: &str,
    user_id: &str,
    service_id: &str,
    units_used: f64,
    service_secret: &str,
    timestamp_secs: Option<f64>,
    usage_path_prefix: Option<&str>,
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
        "serviceId": service_id,
        "unitsUsed": units_used,
        "timestamp": ts_ms
    });
    let body_str = body.to_string();
    let ts_str = ts_ms.to_string();
    let signature = calculate_hmac_with_timestamp(&body_str, &ts_str, service_secret);
    let url = build_usage_report_url(usage_base_url, usage_path_prefix);
    let resp = client
        .post(&url)
        .json(&body)
        .header(headers::SIGNATURE, &signature)
        .header(headers::TIMESTAMP, &ts_str)
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

/// Sends a progress update without an error message.
pub async fn report_progress_simple(
    client: &reqwest::Client,
    progress_url: &str,
    request_id: &str,
    stage: &str,
    percentage_complete: i32,
    service_secret: &str,
) -> bool {
    report_progress(
        client,
        progress_url,
        request_id,
        stage,
        percentage_complete,
        service_secret,
        None,
    )
    .await
}

/// Sends a progress update. Returns `false` if URL has no timestamp or request fails.
pub async fn report_progress(
    client: &reqwest::Client,
    progress_url: &str,
    _request_id: &str,
    stage: &str,
    percentage_complete: i32,
    service_secret: &str,
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
    let signature = calculate_hmac_with_timestamp(&body_str, &timestamp, service_secret);
    let resp = client
        .post(&base_url)
        .json(&body)
        .header(headers::SIGNATURE, signature)
        .header(headers::TIMESTAMP, &timestamp)
        .send()
        .await;
    match resp {
        Ok(r) => r.status().is_success(),
        Err(_) => false,
    }
}

/// Sends completion with status and units only.
pub async fn report_completion(
    client: &reqwest::Client,
    callback_url: &str,
    request_id: &str,
    status: CompletionStatus,
    service_secret: &str,
    units: f64,
) -> bool {
    report_completion_full(
        client,
        callback_url,
        request_id,
        status,
        service_secret,
        None,
        None,
        None,
        units,
    )
    .await
}

/// Sends completion with inline result text.
pub async fn report_completion_with_result(
    client: &reqwest::Client,
    callback_url: &str,
    request_id: &str,
    status: CompletionStatus,
    service_secret: &str,
    result: &str,
    units: f64,
) -> bool {
    report_completion_full(
        client,
        callback_url,
        request_id,
        status,
        service_secret,
        Some(result),
        None,
        None,
        units,
    )
    .await
}

/// Sends a completion notification. Returns `false` if URL has no timestamp or request fails.
pub async fn report_completion_full(
    client: &reqwest::Client,
    callback_url: &str,
    _request_id: &str,
    status: CompletionStatus,
    service_secret: &str,
    result: Option<&str>,
    result_url: Option<&str>,
    content_type: Option<&str>,
    units: f64,
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
        "status": status.as_str(),
        "timestamp": ts_secs,
        "units": units,
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
    let signature = calculate_hmac_with_timestamp(&body_str, &timestamp, service_secret);
    let resp = client
        .post(&base_url)
        .json(&body)
        .header(headers::SIGNATURE, signature)
        .header(headers::TIMESTAMP, &timestamp)
        .send()
        .await;
    match resp {
        Ok(r) => r.status().is_success(),
        Err(_) => false,
    }
}

#[cfg(test)]
mod url_tests {
    use super::build_usage_report_url;

    #[test]
    fn build_usage_report_url_default() {
        assert_eq!(
            build_usage_report_url("http://u.test", None),
            "http://u.test/api/usage/report"
        );
        assert_eq!(
            build_usage_report_url("http://u.test/", None),
            "http://u.test/api/usage/report"
        );
    }

    #[test]
    fn build_usage_report_url_custom_prefix() {
        assert_eq!(
            build_usage_report_url("http://u.test", Some("/usage/api/v1")),
            "http://u.test/usage/api/v1/report"
        );
        assert_eq!(
            build_usage_report_url("http://u.test", Some("usage/api/v1")),
            "http://u.test/usage/api/v1/report"
        );
    }
}
