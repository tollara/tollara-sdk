//! Caller-side gateway polling (sdk-api-spec §1.3–1.4).

/// Gateway invoke HTTP method.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum GatewayHttpMethod {
    Get,
    Post,
    Put,
    Delete,
}

impl GatewayHttpMethod {
    fn as_reqwest_method(self) -> reqwest::Method {
        match self {
            Self::Get => reqwest::Method::GET,
            Self::Post => reqwest::Method::POST,
            Self::Put => reqwest::Method::PUT,
            Self::Delete => reqwest::Method::DELETE,
        }
    }
}

fn normalize_base(url: &str) -> String {
    url.trim_end_matches('/').to_string()
}

fn normalize_prefix(prefix: &str) -> String {
    if prefix.is_empty() {
        return String::new();
    }
    let p = if prefix.starts_with('/') {
        prefix.to_string()
    } else {
        format!("/{}", prefix)
    };
    p.trim_end_matches('/').to_string()
}

fn build_url(gateway_base_url: &str, gateway_path_prefix: &str, suffix: &str) -> String {
    format!(
        "{}{}{}",
        normalize_base(gateway_base_url),
        normalize_prefix(gateway_path_prefix),
        suffix
    )
}

/// Invoke a service endpoint through the Gateway (`.../service/{service_id}/endpoint/{endpoint_id}/invoke[/async]`).
pub async fn invoke_service(
    client: &reqwest::Client,
    gateway_base_url: &str,
    gateway_path_prefix: &str,
    method: GatewayHttpMethod,
    service_id: &str,
    endpoint_id: &str,
    service_key: &str,
    body: Option<&str>,
    is_async: bool,
) -> Result<(u16, String), reqwest::Error> {
    let suffix = format!(
        "/service/{}/endpoint/{}/invoke{}",
        service_id,
        endpoint_id,
        if is_async { "/async" } else { "" }
    );
    let url = build_url(gateway_base_url, gateway_path_prefix, &suffix);
    let mut request = client
        .request(method.as_reqwest_method(), &url)
        .bearer_auth(service_key);
    if matches!(method, GatewayHttpMethod::Post | GatewayHttpMethod::Put) {
        if let Some(payload) = body {
            if !payload.is_empty() {
                request = request
                    .header(reqwest::header::CONTENT_TYPE, "application/json")
                    .body(payload.to_string());
            }
        }
    }
    let res = request.send().await?;
    let status = res.status().as_u16();
    let body = res.text().await.unwrap_or_default();
    Ok((status, body))
}

/// GET `.../requests/{request_id}/status` with Bearer agent key.
pub async fn get_request_status(
    client: &reqwest::Client,
    gateway_base_url: &str,
    gateway_path_prefix: &str,
    request_id: &str,
    service_key: &str,
) -> Result<(bool, u16, String), reqwest::Error> {
    let url = build_url(
        gateway_base_url,
        gateway_path_prefix,
        &format!("/requests/{}/status", request_id),
    );
    let res = client
        .get(&url)
        .bearer_auth(service_key)
        .send()
        .await?;
    let status = res.status().as_u16();
    let ok = res.status().is_success();
    let body = res.text().await.unwrap_or_default();
    Ok((ok, status, body))
}

/// GET `.../requests/{request_id}/result` with Bearer agent key.
pub async fn get_request_result(
    client: &reqwest::Client,
    gateway_base_url: &str,
    gateway_path_prefix: &str,
    request_id: &str,
    service_key: &str,
) -> Result<(bool, u16, String), reqwest::Error> {
    let url = build_url(
        gateway_base_url,
        gateway_path_prefix,
        &format!("/requests/{}/result", request_id),
    );
    let res = client
        .get(&url)
        .bearer_auth(service_key)
        .send()
        .await?;
    let status = res.status().as_u16();
    let ok = res.status().is_success();
    let body = res.text().await.unwrap_or_default();
    Ok((ok, status, body))
}
