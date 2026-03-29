//! Unified HTTP client aligned with Java `AgentVendClient` (env vars, path prefixes, split bases).

use crate::gateway_client;
use crate::usage_client;
use crate::validation_client;

use std::env;

pub const ENV_API_URL: &str = "AGENTVEND_API_URL";
pub const ENV_AGENT_ID: &str = "AGENTVEND_AGENT_ID";
pub const ENV_AGENT_SECRET: &str = "AGENTVEND_AGENT_SECRET";

/// Production API origin; used when neither `api_url` nor `AGENTVEND_API_URL` is set.
pub const DEFAULT_API_URL: &str = "https://api.agentvend.api";

pub const DEFAULT_CORE_PATH_PREFIX: &str = "/api/v1";
pub const DEFAULT_GATEWAY_PATH_PREFIX: &str = "/api";

fn trim_trailing_slashes(s: &str) -> String {
    let mut t = s.trim().to_string();
    while t.ends_with('/') {
        t.pop();
    }
    t
}

fn join_url(base: &str, path: &str) -> String {
    let b = trim_trailing_slashes(base);
    if path.is_empty() {
        return b;
    }
    let p = if path.starts_with('/') {
        path.to_string()
    } else {
        format!("/{}", path)
    };
    b + &p
}

fn first_non_blank(opt_a: Option<&str>, opt_b: Option<&str>) -> String {
    if let Some(a) = opt_a {
        let t = a.trim();
        if !t.is_empty() {
            return t.to_string();
        }
    }
    if let Some(b) = opt_b {
        let t = b.trim();
        if !t.is_empty() {
            return t.to_string();
        }
    }
    String::new()
}

/// Configuration for [`AgentVendClient`]. Unset fields are taken from environment variables where applicable.
#[derive(Debug, Default, Clone)]
pub struct AgentVendClientConfig {
    pub api_url: Option<String>,
    pub core_api_url: Option<String>,
    pub gateway_api_url: Option<String>,
    pub usage_api_url: Option<String>,
    pub core_path_prefix: Option<String>,
    pub gateway_path_prefix: Option<String>,
    pub usage_path_prefix: Option<String>,
    pub agent_id: Option<String>,
    pub agent_secret: Option<String>,
    pub http_client: Option<reqwest::Client>,
}

/// Single entry point for Core validate, Usage report/progress/complete, and Gateway polling.
pub struct AgentVendClient {
    http: reqwest::Client,
    gateway_base: String,
    gateway_path_prefix: String,
    core_root: String,
    usage_base: String,
    usage_path_prefix: Option<String>,
    agent_id: Option<String>,
    agent_secret: String,
}

impl AgentVendClient {
    /// Build from explicit options and/or `AGENTVEND_*` environment variables.
    /// The API origin defaults to [`DEFAULT_API_URL`] when neither `api_url` nor `AGENTVEND_API_URL` is set.
    pub fn try_new(config: AgentVendClientConfig) -> Result<Self, &'static str> {
        let mut resolved = trim_trailing_slashes(&first_non_blank(
            config.api_url.as_deref(),
            env::var(ENV_API_URL).ok().as_deref(),
        ));
        if resolved.is_empty() {
            resolved = trim_trailing_slashes(DEFAULT_API_URL);
        }

        let core_base = trim_trailing_slashes(&first_non_blank(
            config.core_api_url.as_deref(),
            Some(&resolved),
        ));
        let gw_base = trim_trailing_slashes(&first_non_blank(
            config.gateway_api_url.as_deref(),
            Some(&resolved),
        ));
        let usage_base = trim_trailing_slashes(&first_non_blank(
            config.usage_api_url.as_deref(),
            Some(&resolved),
        ));

        let core_prefix = config
            .core_path_prefix
            .as_deref()
            .unwrap_or(DEFAULT_CORE_PATH_PREFIX);
        let gw_prefix = config
            .gateway_path_prefix
            .as_deref()
            .unwrap_or(DEFAULT_GATEWAY_PATH_PREFIX);

        let agent_secret = first_non_blank(
            config.agent_secret.as_deref(),
            env::var(ENV_AGENT_SECRET).ok().as_deref(),
        );
        if agent_secret.is_empty() {
            return Err("Agent secret is required: set agent_secret or AGENTVEND_AGENT_SECRET");
        }

        let agent_id_raw = first_non_blank(
            config.agent_id.as_deref(),
            env::var(ENV_AGENT_ID).ok().as_deref(),
        );
        let agent_id = if agent_id_raw.is_empty() {
            None
        } else {
            Some(agent_id_raw)
        };

        let http = config.http_client.unwrap_or_else(reqwest::Client::new);

        Ok(Self {
            http,
            gateway_base: gw_base,
            gateway_path_prefix: gw_prefix.to_string(),
            core_root: join_url(&core_base, core_prefix),
            usage_base,
            usage_path_prefix: config.usage_path_prefix,
            agent_id,
            agent_secret,
        })
    }

    /// Same as `try_new` with an empty config (env only).
    pub fn try_from_env() -> Result<Self, &'static str> {
        Self::try_new(AgentVendClientConfig::default())
    }

    /// Resolves the usage report URL for the configured base and prefix (for tests / debugging).
    #[must_use]
    pub fn usage_report_url(&self) -> String {
        usage_client::build_usage_report_url(
            &self.usage_base,
            self.usage_path_prefix.as_deref(),
        )
    }

    pub async fn validate_agent_key(
        &self,
        agent_key: &str,
    ) -> Option<validation_client::AgentKeyValidationResult> {
        validation_client::validate_agent_key(
            &self.http,
            &self.core_root,
            agent_key,
            &self.agent_secret,
            self.agent_id.as_deref(),
        )
        .await
    }

    pub async fn report_usage(
        &self,
        user_id: &str,
        agent_id: &str,
        units_used: f64,
    ) -> Result<usage_client::UsageReportResponse, Box<dyn std::error::Error + Send + Sync>> {
        usage_client::report_usage_at(
            &self.http,
            &self.usage_base,
            user_id,
            agent_id,
            units_used,
            &self.agent_secret,
            None,
            self.usage_path_prefix.as_deref(),
        )
        .await
    }

    pub async fn report_usage_at(
        &self,
        user_id: &str,
        agent_id: &str,
        units_used: f64,
        timestamp_secs: Option<f64>,
    ) -> Result<usage_client::UsageReportResponse, Box<dyn std::error::Error + Send + Sync>> {
        usage_client::report_usage_at(
            &self.http,
            &self.usage_base,
            user_id,
            agent_id,
            units_used,
            &self.agent_secret,
            timestamp_secs,
            self.usage_path_prefix.as_deref(),
        )
        .await
    }

    pub async fn send_progress_update(
        &self,
        progress_url: &str,
        request_id: &str,
        stage: &str,
        percentage_complete: i32,
        error_message: Option<&str>,
    ) -> bool {
        usage_client::report_progress(
            &self.http,
            progress_url,
            request_id,
            stage,
            percentage_complete,
            &self.agent_secret,
            error_message,
        )
        .await
    }

    pub async fn send_completion(
        &self,
        callback_url: &str,
        request_id: &str,
        status: usage_client::CompletionStatus,
        units: f64,
    ) -> bool {
        usage_client::report_completion(
            &self.http,
            callback_url,
            request_id,
            status,
            &self.agent_secret,
            units,
        )
        .await
    }

    pub async fn send_completion_with_result(
        &self,
        callback_url: &str,
        request_id: &str,
        status: usage_client::CompletionStatus,
        result: &str,
        units: f64,
    ) -> bool {
        usage_client::report_completion_with_result(
            &self.http,
            callback_url,
            request_id,
            status,
            &self.agent_secret,
            result,
            units,
        )
        .await
    }

    pub async fn send_completion_full(
        &self,
        callback_url: &str,
        request_id: &str,
        status: usage_client::CompletionStatus,
        result: Option<&str>,
        result_url: Option<&str>,
        content_type: Option<&str>,
        units: f64,
    ) -> bool {
        usage_client::report_completion_full(
            &self.http,
            callback_url,
            request_id,
            status,
            &self.agent_secret,
            result,
            result_url,
            content_type,
            units,
        )
        .await
    }

    pub async fn get_request_status(
        &self,
        request_id: &str,
        agent_key: &str,
    ) -> Result<(bool, u16, String), reqwest::Error> {
        gateway_client::get_request_status(
            &self.http,
            &self.gateway_base,
            &self.gateway_path_prefix,
            request_id,
            agent_key,
        )
        .await
    }

    pub async fn get_request_result(
        &self,
        request_id: &str,
        agent_key: &str,
    ) -> Result<(bool, u16, String), reqwest::Error> {
        gateway_client::get_request_result(
            &self.http,
            &self.gateway_base,
            &self.gateway_path_prefix,
            request_id,
            agent_key,
        )
        .await
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn usage_report_url_default() {
        let c = AgentVendClient::try_new(AgentVendClientConfig {
            api_url: Some("http://u.test".into()),
            agent_secret: Some("s".into()),
            ..Default::default()
        })
        .unwrap();
        assert_eq!(c.usage_report_url(), "http://u.test/api/usage/report");
    }

    #[test]
    fn try_new_uses_constant_default_when_api_url_explicit() {
        let c = AgentVendClient::try_new(AgentVendClientConfig {
            api_url: Some(DEFAULT_API_URL.into()),
            agent_secret: Some("s".into()),
            ..Default::default()
        })
        .unwrap();
        assert_eq!(
            c.usage_report_url(),
            format!("{DEFAULT_API_URL}/api/usage/report")
        );
    }

    #[test]
    fn usage_report_url_custom_prefix() {
        let c = AgentVendClient::try_new(AgentVendClientConfig {
            api_url: Some("http://u.test".into()),
            agent_secret: Some("s".into()),
            usage_path_prefix: Some("/usage/api/v1".into()),
            ..Default::default()
        })
        .unwrap();
        assert_eq!(
            c.usage_report_url(),
            "http://u.test/usage/api/v1/report"
        );
    }
}
