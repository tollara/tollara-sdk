//! Unified HTTP client aligned with Java `TollaraClient` (env vars, path prefixes, split bases).

use crate::gateway_client;
use crate::usage_client;
use crate::validation_client;

use std::env;

pub const ENV_API_URL: &str = "TOLLARA_API_URL";
pub const ENV_SERVICE_ID: &str = "TOLLARA_SERVICE_ID";
pub const ENV_SERVICE_SECRET: &str = "TOLLARA_SERVICE_SECRET";
pub const ENV_AGENT_ID: &str = "TOLLARA_AGENT_ID";
pub const ENV_AGENT_SECRET: &str = "TOLLARA_AGENT_SECRET";

/// Production API origin; used when neither `api_url` nor `TOLLARA_API_URL` is set.
pub const DEFAULT_API_URL: &str = "https://api.tollara.ai";

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

/// Configuration for [`TollaraClient`]. Unset fields are taken from environment variables where applicable.
#[derive(Debug, Default, Clone)]
pub struct TollaraClientConfig {
    pub api_url: Option<String>,
    pub core_api_url: Option<String>,
    pub gateway_api_url: Option<String>,
    pub usage_api_url: Option<String>,
    pub core_path_prefix: Option<String>,
    pub gateway_path_prefix: Option<String>,
    pub usage_path_prefix: Option<String>,
    pub service_id: Option<String>,
    pub service_secret: Option<String>,
    pub http_client: Option<reqwest::Client>,
}

/// Single entry point for Core validate, Usage report/progress/complete, and Gateway polling.
pub struct TollaraClient {
    http: reqwest::Client,
    gateway_base: String,
    gateway_path_prefix: String,
    core_root: String,
    usage_base: String,
    usage_path_prefix: Option<String>,
    service_id: Option<String>,
    service_secret: String,
}

impl TollaraClient {
    /// Build from explicit options and/or `TOLLARA_*` environment variables.
    /// The API origin defaults to [`DEFAULT_API_URL`] when neither `api_url` nor `TOLLARA_API_URL` is set.
    pub fn try_new(config: TollaraClientConfig) -> Result<Self, &'static str> {
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

        let service_secret = first_non_blank(
            config.service_secret.as_deref(),
            env::var(ENV_SERVICE_SECRET)
                .ok()
                .as_deref()
                .or_else(|| env::var(ENV_AGENT_SECRET).ok().as_deref()),
        );
        if service_secret.is_empty() {
            return Err("Service secret is required: set service_secret or TOLLARA_SERVICE_SECRET");
        }

        let service_id_raw = first_non_blank(
            config.service_id.as_deref(),
            env::var(ENV_SERVICE_ID)
                .ok()
                .as_deref()
                .or_else(|| env::var(ENV_AGENT_ID).ok().as_deref()),
        );
        let service_id = if service_id_raw.is_empty() {
            None
        } else {
            Some(service_id_raw)
        };

        let http = config.http_client.unwrap_or_else(reqwest::Client::new);

        Ok(Self {
            http,
            gateway_base: gw_base,
            gateway_path_prefix: gw_prefix.to_string(),
            core_root: join_url(&core_base, core_prefix),
            usage_base,
            usage_path_prefix: config.usage_path_prefix,
            service_id,
            service_secret,
        })
    }

    /// Same as `try_new` with an empty config (env only).
    pub fn try_from_env() -> Result<Self, &'static str> {
        Self::try_new(TollaraClientConfig::default())
    }

    /// Resolves the usage report URL for the configured base and prefix (for tests / debugging).
    #[must_use]
    pub fn usage_report_url(&self) -> String {
        usage_client::build_usage_report_url(
            &self.usage_base,
            self.usage_path_prefix.as_deref(),
        )
    }

    pub async fn validate_service_key(
        &self,
        service_key: &str,
    ) -> Option<validation_client::ServiceKeyValidationResult> {
        validation_client::validate_service_key(
            &self.http,
            &self.core_root,
            service_key,
            &self.service_secret,
            self.service_id.as_deref(),
        )
        .await
    }

    pub async fn estimate_usage(
        &self,
        service_key: &str,
        estimated_units: f64,
    ) -> Option<validation_client::UsageEstimateResult> {
        validation_client::estimate_usage(
            &self.http,
            &self.core_root,
            service_key,
            &self.service_secret,
            estimated_units,
            self.service_id.as_deref(),
        )
        .await
    }

    pub async fn estimate_usage_with_jwt(
        &self,
        bearer_token: &str,
        user_id: &str,
        service_id: &str,
        estimated_units: f64,
    ) -> Option<validation_client::UsageEstimateResult> {
        validation_client::estimate_usage_with_jwt(
            &self.http,
            &self.core_root,
            bearer_token,
            user_id,
            service_id,
            estimated_units,
        )
        .await
    }

    pub async fn invoke_service(
        &self,
        method: gateway_client::GatewayHttpMethod,
        service_id: &str,
        endpoint_id: &str,
        service_key: &str,
        body: Option<&str>,
        is_async: bool,
    ) -> Result<(u16, String), reqwest::Error> {
        gateway_client::invoke_service(
            &self.http,
            &self.gateway_base,
            &self.gateway_path_prefix,
            method,
            service_id,
            endpoint_id,
            service_key,
            body,
            is_async,
        )
        .await
    }

    pub async fn report_usage(
        &self,
        user_id: &str,
        service_id: &str,
        units_used: f64,
    ) -> Result<usage_client::UsageReportResponse, Box<dyn std::error::Error + Send + Sync>> {
        usage_client::report_usage_at(
            &self.http,
            &self.usage_base,
            user_id,
            service_id,
            units_used,
            &self.service_secret,
            None,
            self.usage_path_prefix.as_deref(),
        )
        .await
    }

    pub async fn report_usage_at(
        &self,
        user_id: &str,
        service_id: &str,
        units_used: f64,
        timestamp_secs: Option<f64>,
    ) -> Result<usage_client::UsageReportResponse, Box<dyn std::error::Error + Send + Sync>> {
        usage_client::report_usage_at(
            &self.http,
            &self.usage_base,
            user_id,
            service_id,
            units_used,
            &self.service_secret,
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
            &self.service_secret,
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
            &self.service_secret,
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
            &self.service_secret,
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
            &self.service_secret,
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
        service_key: &str,
    ) -> Result<(bool, u16, String), reqwest::Error> {
        gateway_client::get_request_status(
            &self.http,
            &self.gateway_base,
            &self.gateway_path_prefix,
            request_id,
            service_key,
        )
        .await
    }

    pub async fn get_request_result(
        &self,
        request_id: &str,
        service_key: &str,
    ) -> Result<(bool, u16, String), reqwest::Error> {
        gateway_client::get_request_result(
            &self.http,
            &self.gateway_base,
            &self.gateway_path_prefix,
            request_id,
            service_key,
        )
        .await
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn usage_report_url_default() {
        let c = TollaraClient::try_new(TollaraClientConfig {
            api_url: Some("http://u.test".into()),
            service_secret: Some("s".into()),
            ..Default::default()
        })
        .unwrap();
        assert_eq!(c.usage_report_url(), "http://u.test/api/usage/report");
    }

    #[test]
    fn try_new_uses_constant_default_when_api_url_explicit() {
        let c = TollaraClient::try_new(TollaraClientConfig {
            api_url: Some(DEFAULT_API_URL.into()),
            service_secret: Some("s".into()),
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
        let c = TollaraClient::try_new(TollaraClientConfig {
            api_url: Some("http://u.test".into()),
            service_secret: Some("s".into()),
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
