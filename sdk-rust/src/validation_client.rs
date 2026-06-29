//! Client for validating service keys via the Core API (see docs/sdk-api-spec.md §2).

use crate::headers;
use crate::hmac::{grant_access, validate_hmac_signature};
use serde::Deserialize;
use serde::Serialize;
use std::collections::HashMap;

/// Shared usage breakdown for estimate and report responses.
#[derive(Debug, Clone, Deserialize, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct UsageBreakdown {
    pub units_used: Option<f64>,
    pub base_units_used: Option<f64>,
    pub overage_units: Option<f64>,
    pub chargeable_overage_units: Option<f64>,
    pub surplus_overage_units: Option<f64>,
    pub overage_cost: Option<f64>,
    pub total_overage_cost: Option<f64>,
    pub units_remaining: Option<f64>,
    pub remaining_credits: Option<f64>,
    pub remaining_spending_cap: Option<f64>,
    pub total_units_used_this_cycle: Option<f64>,
    #[serde(rename = "isOverLimit")]
    pub over_limit: Option<bool>,
    #[serde(rename = "isOverage")]
    pub overage: Option<bool>,
    #[serde(rename = "isOverageAllowed")]
    pub overage_allowed: Option<bool>,
}

/// Result of a successful service key validation (validationSchemaVersion 3).
#[derive(Debug, Clone)]
pub struct ServiceKeyValidationResult {
    pub user_id: Option<String>,
    pub service_id: Option<String>,
    pub service_key_id: Option<String>,
    pub service_product_id: Option<String>,
    pub roles: Vec<String>,
    pub subscription_status: Option<String>,
    pub validation_schema_version: Option<i32>,
    pub billing_model_type: Option<String>,
    pub measurement_type: Option<String>,
    pub unit_label: Option<String>,
}

impl ServiceKeyValidationResult {
    #[must_use]
    pub fn grant_access(&self) -> bool {
        grant_access(self.subscription_status.as_deref())
    }
}

/// Result of a usage estimate call (estimateSchemaVersion 3).
#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UsageEstimateResult {
    pub sufficient_credits: bool,
    pub would_exceed_cap: bool,
    pub would_allow: bool,
    pub estimated_cost: Option<f64>,
    pub billing_model_type: Option<String>,
    pub measurement_type: Option<String>,
    pub unit_label: Option<String>,
    pub breakdown: Option<UsageBreakdown>,
    pub estimate_schema_version: Option<i64>,
    pub timestamp: Option<i64>,
    #[serde(skip)]
    pub http_status: i32,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ValidateRequest<'a> {
    service_key: &'a str,
    #[serde(skip_serializing_if = "Option::is_none")]
    service_id: Option<&'a str>,
    service_secret: &'a str,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct EstimateRequest<'a> {
    service_key: &'a str,
    #[serde(skip_serializing_if = "Option::is_none")]
    service_id: Option<&'a str>,
    service_secret: &'a str,
    estimated_units: f64,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct JwtEstimateRequest<'a> {
    user_id: &'a str,
    service_id: &'a str,
    estimated_units: f64,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ValidationResponse {
    valid: bool,
    service_key_id: Option<String>,
    user_id: Option<String>,
    service_id: Option<String>,
    service_product_id: Option<String>,
    roles: Option<Vec<String>>,
    subscription_status: Option<String>,
    billing_model_type: Option<String>,
    measurement_type: Option<String>,
    unit_label: Option<String>,
    #[allow(dead_code)]
    timestamp: Option<i64>,
    error: Option<String>,
    validation_schema_version: Option<i32>,
}

/// Canonical failure codes for validate outcome (§2.1.1).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ValidationFailureCode {
    MissingKey,
    Network,
    HttpError,
    MissingSignatureHeaders,
    HmacMismatch,
    InvalidKey,
    ParseError,
}

impl ValidationFailureCode {
    #[must_use]
    pub fn as_str(self) -> &'static str {
        match self {
            Self::MissingKey => "MISSING_KEY",
            Self::Network => "NETWORK",
            Self::HttpError => "HTTP_ERROR",
            Self::MissingSignatureHeaders => "MISSING_SIGNATURE_HEADERS",
            Self::HmacMismatch => "HMAC_MISMATCH",
            Self::InvalidKey => "INVALID_KEY",
            Self::ParseError => "PARSE_ERROR",
        }
    }
}

#[derive(Debug, Clone)]
pub struct ServiceKeyValidationFailure {
    pub code: ValidationFailureCode,
    pub message: Option<String>,
    pub http_status: Option<i32>,
}

#[derive(Debug, Clone)]
pub enum ServiceKeyValidationOutcome {
    Success(ServiceKeyValidationResult),
    Failure(ServiceKeyValidationFailure),
}

impl ServiceKeyValidationOutcome {
    #[must_use]
    pub fn ok(&self) -> bool {
        matches!(self, Self::Success(_))
    }
}

/// Validates a service key via the Core service with structured outcome (§2.1.1).
pub async fn validate_service_key_with_outcome(
    client: &reqwest::Client,
    core_base_url: &str,
    service_key: &str,
    service_secret: &str,
    service_id: Option<&str>,
) -> ServiceKeyValidationOutcome {
    if service_key.trim().is_empty() {
        return ServiceKeyValidationOutcome::Failure(ServiceKeyValidationFailure {
            code: ValidationFailureCode::MissingKey,
            message: None,
            http_status: None,
        });
    }
    let url = format!(
        "{}/service-keys/validate",
        core_base_url.trim_end_matches('/')
    );
    let body = ValidateRequest {
        service_key,
        service_id,
        service_secret,
    };
    let resp = match client.post(&url).json(&body).send().await {
        Ok(r) => r,
        Err(_) => {
            return ServiceKeyValidationOutcome::Failure(ServiceKeyValidationFailure {
                code: ValidationFailureCode::Network,
                message: None,
                http_status: None,
            });
        }
    };
    let http_status = i32::from(resp.status().as_u16());
    if !resp.status().is_success() {
        return ServiceKeyValidationOutcome::Failure(ServiceKeyValidationFailure {
            code: ValidationFailureCode::HttpError,
            message: None,
            http_status: Some(http_status),
        });
    }
    let signature = match resp.headers().get(headers::SIGNATURE).and_then(|v| v.to_str().ok()) {
        Some(s) => s,
        None => {
            return ServiceKeyValidationOutcome::Failure(ServiceKeyValidationFailure {
                code: ValidationFailureCode::MissingSignatureHeaders,
                message: None,
                http_status: Some(http_status),
            });
        }
    };
    let timestamp = match resp.headers().get(headers::TIMESTAMP).and_then(|v| v.to_str().ok()) {
        Some(t) => t,
        None => {
            return ServiceKeyValidationOutcome::Failure(ServiceKeyValidationFailure {
                code: ValidationFailureCode::MissingSignatureHeaders,
                message: None,
                http_status: Some(http_status),
            });
        }
    };
    let response_text = match resp.text().await {
        Ok(t) => t,
        Err(_) => {
            return ServiceKeyValidationOutcome::Failure(ServiceKeyValidationFailure {
                code: ValidationFailureCode::Network,
                message: None,
                http_status: Some(http_status),
            });
        }
    };
    if !validate_hmac_signature(signature, &format!("{}{}", response_text, timestamp), service_secret)
    {
        return ServiceKeyValidationOutcome::Failure(ServiceKeyValidationFailure {
            code: ValidationFailureCode::HmacMismatch,
            message: None,
            http_status: Some(http_status),
        });
    }
    let data: ValidationResponse = match serde_json::from_str(&response_text) {
        Ok(d) => d,
        Err(_) => {
            return ServiceKeyValidationOutcome::Failure(ServiceKeyValidationFailure {
                code: ValidationFailureCode::ParseError,
                message: None,
                http_status: Some(http_status),
            });
        }
    };
    if !data.valid {
        return ServiceKeyValidationOutcome::Failure(ServiceKeyValidationFailure {
            code: ValidationFailureCode::InvalidKey,
            message: data.error,
            http_status: Some(http_status),
        });
    }
    let roles = data.roles.unwrap_or_default();
    ServiceKeyValidationOutcome::Success(ServiceKeyValidationResult {
        user_id: data.user_id,
        service_id: data.service_id.or(service_id.map(String::from)),
        service_key_id: data.service_key_id,
        service_product_id: data.service_product_id,
        roles,
        subscription_status: data.subscription_status,
        validation_schema_version: data.validation_schema_version,
        billing_model_type: data.billing_model_type,
        measurement_type: data.measurement_type,
        unit_label: data.unit_label,
    })
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
    match validate_service_key_with_outcome(client, core_base_url, service_key, service_secret, service_id).await {
        ServiceKeyValidationOutcome::Success(r) => Some(r),
        ServiceKeyValidationOutcome::Failure(_) => None,
    }
}

/// Estimates usage via Core service-key endpoint (`.../service-keys/estimate-usage`).
pub async fn estimate_usage(
    client: &reqwest::Client,
    core_base_url: &str,
    service_key: &str,
    service_secret: &str,
    estimated_units: f64,
    service_id: Option<&str>,
) -> Option<UsageEstimateResult> {
    if estimated_units <= 0.0 || service_key.trim().is_empty() {
        return None;
    }
    let url = format!(
        "{}/service-keys/estimate-usage",
        core_base_url.trim_end_matches('/')
    );
    let body = EstimateRequest {
        service_key,
        service_id,
        service_secret,
        estimated_units,
    };
    let resp = client.post(&url).json(&body).send().await.ok()?;
    let code = resp.status().as_u16();
    if code != 200 && code != 403 && code != 429 {
        return None;
    }
    let signature = resp
        .headers()
        .get(headers::SIGNATURE)
        .and_then(|v| v.to_str().ok())?;
    let timestamp = resp
        .headers()
        .get(headers::TIMESTAMP)
        .and_then(|v| v.to_str().ok())?;
    let response_text = resp.text().await.ok()?;
    if response_text.trim().is_empty() {
        return None;
    }
    if !validate_hmac_signature(signature, &format!("{}{}", response_text, timestamp), service_secret)
    {
        return None;
    }
    let mut parsed: UsageEstimateResult = serde_json::from_str(&response_text).ok()?;
    parsed.http_status = i32::from(code);
    Some(parsed)
}

/// Estimates usage via Core JWT endpoint (`.../billing/usage/estimate`). Response is unsigned.
pub async fn estimate_usage_with_jwt(
    client: &reqwest::Client,
    core_base_url: &str,
    bearer_token: &str,
    user_id: &str,
    service_id: &str,
    estimated_units: f64,
) -> Option<UsageEstimateResult> {
    if estimated_units <= 0.0
        || bearer_token.trim().is_empty()
        || user_id.trim().is_empty()
        || service_id.trim().is_empty()
    {
        return None;
    }
    let url = format!(
        "{}/billing/usage/estimate",
        core_base_url.trim_end_matches('/')
    );
    let body = JwtEstimateRequest {
        user_id,
        service_id,
        estimated_units,
    };
    let resp = client
        .post(&url)
        .bearer_auth(bearer_token.trim())
        .json(&body)
        .send()
        .await
        .ok()?;
    let code = resp.status().as_u16();
    if code != 200 && code != 403 && code != 429 {
        return None;
    }
    let response_text = resp.text().await.ok()?;
    if response_text.trim().is_empty() {
        return None;
    }
    let mut parsed: UsageEstimateResult = serde_json::from_str(&response_text).ok()?;
    parsed.http_status = i32::from(code);
    Some(parsed)
}
