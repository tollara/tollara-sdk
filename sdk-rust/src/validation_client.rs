//! Client for validating agent keys via the Core API (see docs/sdk-api-spec.md §2).

use crate::hmac::validate_hmac_signature;
use serde::Deserialize;
use serde::Serialize;

/// Result of a successful agent key validation.
#[derive(Debug, Clone)]
pub struct AgentKeyValidationResult {
    pub user_id: Option<String>,
    pub agent_id: Option<String>,
    pub plan: Option<String>,
    pub roles: Vec<String>,
    pub quota_remaining: Option<f64>,
    pub subscription_active: bool,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ValidateRequest<'a> {
    agent_key: &'a str,
    #[serde(skip_serializing_if = "Option::is_none")]
    agent_id: Option<&'a str>,
    agent_secret: &'a str,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ValidationResponse {
    valid: bool,
    user_id: Option<String>,
    agent_id: Option<String>,
    plan: Option<String>,
    roles: Option<Vec<String>>,
    quota_remaining: Option<f64>,
    subscription_active: bool,
    #[allow(dead_code)]
    timestamp: Option<i64>,
    error: Option<String>,
}

/// Validates an agent key via the Core service. Returns `None` on 4xx/5xx, missing HMAC headers,
/// invalid signature, or when the response has `valid: false`.
pub async fn validate_agent_key(
    client: &reqwest::Client,
    core_base_url: &str,
    agent_key: &str,
    agent_secret: &str,
    agent_id: Option<&str>,
) -> Option<AgentKeyValidationResult> {
    let url = format!(
        "{}/agent-keys/validate",
        core_base_url.trim_end_matches('/')
    );
    let body = ValidateRequest {
        agent_key,
        agent_id,
        agent_secret,
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
        .get("x-marketplace-signature")
        .and_then(|v| v.to_str().ok())?;
    let timestamp = resp
        .headers()
        .get("x-marketplace-timestamp")
        .and_then(|v| v.to_str().ok())?;
    if !validate_hmac_signature(signature, &format!("{}{}", response_text, timestamp), agent_secret)
    {
        return None;
    }
    let data: ValidationResponse = serde_json::from_str(&response_text).ok()?;
    if !data.valid {
        return None;
    }
    let roles = data.roles.unwrap_or_default();
    Some(AgentKeyValidationResult {
        user_id: data.user_id,
        agent_id: data.agent_id.or(agent_id.map(String::from)),
        plan: data.plan,
        roles,
        quota_remaining: data.quota_remaining,
        subscription_active: data.subscription_active,
    })
}
