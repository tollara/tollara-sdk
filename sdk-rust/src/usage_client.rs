//! Client for the Usage API: report usage, progress, completion (see docs/sdk-api-spec.md §3).

use crate::headers;
use crate::hmac::calculate_hmac_with_timestamp;
use serde::Deserialize;

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

/// Result of a progress or completion callback POST to the usage service.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct UsageCallbackResult {
    pub success: bool,
    pub http_status: u16,
    pub http_status_text: String,
    pub request_url: String,
    pub response_body: Option<String>,
    pub network_error: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct UsageReportResponseJson {
    status: Option<String>,
    is_over_limit: Option<bool>,
    remaining_requests_per_period: Option<i64>,
}

/// Default path segment before `/report` (matches Java `TollaraUrls.DEFAULT_USAGE_PATH_PREFIX`).
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
    if url.is_empty() {
        return (String::new(), None);
    }
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

async fn post_signed_usage_callback(
    client: &reqwest::Client,
    url_with_query: &str,
    body: &serde_json::Value,
    body_str: &str,
    service_secret: &str,
) -> UsageCallbackResult {
    let (base_url, timestamp) = parse_timestamp_from_url(url_with_query);
    let timestamp = match timestamp {
        Some(t) => t,
        None => {
            let status_text = if url_with_query.is_empty() {
                "Missing or invalid callback/progress URL"
            } else {
                "Missing timestamp query parameter in URL"
            };
            return UsageCallbackResult {
                success: false,
                http_status: 0,
                http_status_text: status_text.to_string(),
                request_url: base_url,
                response_body: None,
                network_error: None,
            };
        }
    };
    let signature = calculate_hmac_with_timestamp(body_str, &timestamp, service_secret);
    let resp = client
        .post(&base_url)
        .json(body)
        .header(headers::SIGNATURE, signature)
        .header(headers::TIMESTAMP, &timestamp)
        .send()
        .await;
    match resp {
        Ok(r) => {
            let http_status = r.status().as_u16();
            let success = r.status().is_success();
            let http_status_text = r
                .status()
                .canonical_reason()
                .map(str::to_string)
                .unwrap_or_else(|| {
                    if success {
                        "OK".to_string()
                    } else {
                        format!("HTTP {http_status}")
                    }
                });
            let response_body = r.text().await.ok().filter(|s| !s.is_empty());
            UsageCallbackResult {
                success,
                http_status,
                http_status_text,
                request_url: base_url,
                response_body,
                network_error: None,
            }
        }
        Err(e) => UsageCallbackResult {
            success: false,
            http_status: 0,
            http_status_text: "Network error".to_string(),
            request_url: base_url,
            response_body: None,
            network_error: Some(e.to_string()),
        },
    }
}

/// Sends a progress update. Returns structured callback result (see JS `UsageCallbackResult`).
pub async fn report_progress(
    client: &reqwest::Client,
    progress_url: &str,
    _request_id: &str,
    stage: &str,
    percentage_complete: i32,
    service_secret: &str,
    error_message: Option<&str>,
) -> UsageCallbackResult {
    let now_iso = chrono::Utc::now().to_rfc3339_opts(chrono::SecondsFormat::Secs, true);
    let mut body = serde_json::json!({
        "stage": stage,
        "percentageComplete": percentage_complete,
        "timestamp": now_iso,
    });
    if let Some(msg) = error_message {
        body["errorMessage"] = serde_json::Value::String(msg.to_string());
    }
    let body_str = body.to_string();
    post_signed_usage_callback(client, progress_url, &body, &body_str, service_secret).await
}

/// Sends a completion notification with optional result fields.
pub async fn report_completion(
    client: &reqwest::Client,
    callback_url: &str,
    _request_id: &str,
    status: CompletionStatus,
    service_secret: &str,
    units: f64,
    result: Option<&str>,
    result_url: Option<&str>,
    content_type: Option<&str>,
) -> UsageCallbackResult {
    let now_iso = chrono::Utc::now().to_rfc3339_opts(chrono::SecondsFormat::Secs, true);
    let mut body = serde_json::json!({
        "status": status.as_str(),
        "timestamp": now_iso,
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
    post_signed_usage_callback(client, callback_url, &body, &body_str, service_secret).await
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
    let ts_sec = timestamp_secs
        .map(|t| t as i64)
        .unwrap_or_else(|| {
            SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap()
                .as_secs() as i64
        });
    let iso_ts = chrono::DateTime::<chrono::Utc>::from_timestamp(ts_sec, 0)
        .unwrap_or_else(chrono::Utc::now)
        .to_rfc3339_opts(chrono::SecondsFormat::Secs, true);
    let body = serde_json::json!({
        "userId": user_id,
        "serviceId": service_id,
        "unitsUsed": units_used,
        "timestamp": iso_ts
    });
    let body_str = body.to_string();
    let ts_str = ts_sec.to_string();
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
