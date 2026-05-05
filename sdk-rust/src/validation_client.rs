//! Client for validating service keys via the Core API (see docs/sdk-api-spec.md §2).

use crate::headers;
use crate::hmac::validate_hmac_signature;
use serde::Deserialize;
use serde::Serialize;

/// Result of a successful service key validation.
#[derive(Debug, Clone)]
pub struct ServiceKeyValidationResult {
    pub user_id: Option<String>,
    pub service_id: Option<String>,
    pub plan: Option<String>,
    pub roles: Vec<String>,
    pub quota_remaining: Option<f64>,
    pub subscription_active: bool,
    pub billing_model_type: Option<String>,
    pub measurement_type: Option<String>,
    pub unit_label: Option<String>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ValidateRequest<'a> {
    service_key: &'a str,
    #[serde(skip_serializing_if = "Option::is_none")]
    service_id: Option<&'a str>,
    service_secret: &'a str,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ValidationResponse {
    valid: bool,
    user_id: Option<String>,
    service_id: Option<String>,
    plan: Option<String>,
    roles: Option<Vec<String>>,
    quota_remaining: Option<f64>,
    subscription_active: bool,
    billing_model_type: Option<String>,
    measurement_type: Option<String>,
    unit_label: Option<String>,
    #[allow(dead_code)]
    timestamp: Option<i64>,
    error: Option<String>,
}

/// Validates a service key via the Core service. Returns `None` on 4xx/5xx, missing HMAC headers,
/// invalid signature, or when the response has `valid: false`.
pub async fn validate_service_key(
    client: &reqwest::Client,
    core_base_url: &str,
    service_key: &str,
    service_secret: &str,
    service_id: Option<&str>,
) -> Option<ServiceKeyValidationResult> {
    let url = format!(
        "{}/service-keys/validate",
        core_base_url.trim_end_matches('/')
    );
    let body = ValidateRequest {
        service_key,
        service_id,
        service_secret,
    };
    let resp = client
        .post(&url)
        .json(&body)
        .send()
        .await
        .ok()?;
    if !resp.status().is_success() {
        return None;
    }
    let response_text = resp.text().await.ok()?;
    let signature = resp
        .headers()
        .get(headers::SIGNATURE)
        .and_then(|v| v.to_str().ok())?;
    let timestamp = resp
        .headers()
        .get(headers::TIMESTAMP)
        .and_then(|v| v.to_str().ok())?;
    if !validate_hmac_signature(signature, &format!("{}{}", response_text, timestamp), service_secret)
    {
        return None;
    }
    let data: ValidationResponse = serde_json::from_str(&response_text).ok()?;
    if !data.valid {
        return None;
    }
    let roles = data.roles.unwrap_or_default();
    Some(ServiceKeyValidationResult {
        user_id: data.user_id,
        service_id: data.service_id.or(service_id.map(String::from)),
        plan: data.plan,
        roles,
        quota_remaining: data.quota_remaining,
        subscription_active: data.subscription_active,
        billing_model_type: data.billing_model_type,
        measurement_type: data.measurement_type,
        unit_label: data.unit_label,
    })
}
