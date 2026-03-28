//! Caller-side gateway polling (sdk-api-spec §1.3–1.4).

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

/// GET `.../requests/{request_id}/status` with Bearer agent key.
pub async fn get_request_status(
    client: &reqwest::Client,
    gateway_base_url: &str,
    gateway_path_prefix: &str,
    request_id: &str,
    agent_key: &str,
) -> Result<(bool, u16, String), reqwest::Error> {
    let url = build_url(
        gateway_base_url,
        gateway_path_prefix,
        &format!("/requests/{}/status", request_id),
    );
    let res = client
        .get(&url)
        .bearer_auth(agent_key)
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
    agent_key: &str,
) -> Result<(bool, u16, String), reqwest::Error> {
    let url = build_url(
        gateway_base_url,
        gateway_path_prefix,
        &format!("/requests/{}/result", request_id),
    );
    let res = client
        .get(&url)
        .bearer_auth(agent_key)
        .send()
        .await?;
    let status = res.status().as_u16();
    let ok = res.status().is_success();
    let body = res.text().await.unwrap_or_default();
    Ok((ok, status, body))
}
