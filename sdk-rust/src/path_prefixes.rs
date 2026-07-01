//! ECS vs Docker path prefixes for hosted Tollara API origins (parity with sdk-js).

pub const ECS_CORE_PATH_PREFIX: &str = "/core/api/v1";
pub const ECS_GATEWAY_PATH_PREFIX: &str = "/gateway/api/v1";
pub const ECS_USAGE_PATH_PREFIX: &str = "/usage/api/v1";

pub fn is_hosted_tollara_api_origin(origin: &str) -> bool {
    let origin = origin.trim();
    let rest = origin
        .strip_prefix("https://")
        .or_else(|| origin.strip_prefix("http://"))
        .unwrap_or(origin);
    let host = rest
        .split('/')
        .next()
        .unwrap_or("")
        .split(':')
        .next()
        .unwrap_or("")
        .to_lowercase();
    if host.is_empty() {
        return false;
    }
    if host == "api.tollara.ai" || host.ends_with(".api.tollara.ai") {
        return true;
    }
    host == "api.ppe.tollara.ai" || host.ends_with(".api.ppe.tollara.ai")
}

fn resolve_origin(base_url: Option<&str>, default_api_url: &str) -> String {
    if let Some(url) = base_url {
        let t = url.trim();
        if !t.is_empty() {
            return trim_trailing_slashes(t);
        }
    }
    trim_trailing_slashes(default_api_url)
}

fn trim_trailing_slashes(s: &str) -> String {
    let mut t = s.trim().to_string();
    while t.ends_with('/') {
        t.pop();
    }
    t
}

pub fn resolve_gateway_path_prefix(
    base_url: Option<&str>,
    default_api_url: &str,
    default_gateway_prefix: &str,
    override_prefix: Option<&str>,
) -> String {
    if let Some(p) = override_prefix {
        let t = p.trim();
        if !t.is_empty() {
            return t.to_string();
        }
    }
    let origin = resolve_origin(base_url, default_api_url);
    if is_hosted_tollara_api_origin(&origin) {
        ECS_GATEWAY_PATH_PREFIX.to_string()
    } else {
        default_gateway_prefix.to_string()
    }
}

pub fn resolve_core_path_prefix(
    base_url: Option<&str>,
    default_api_url: &str,
    default_core_prefix: &str,
    override_prefix: Option<&str>,
) -> String {
    if let Some(p) = override_prefix {
        let t = p.trim();
        if !t.is_empty() {
            return t.to_string();
        }
    }
    let origin = resolve_origin(base_url, default_api_url);
    if is_hosted_tollara_api_origin(&origin) {
        ECS_CORE_PATH_PREFIX.to_string()
    } else {
        default_core_prefix.to_string()
    }
}

pub fn resolve_usage_path_prefix(
    base_url: Option<&str>,
    default_api_url: &str,
    default_usage_prefix: &str,
    override_prefix: Option<&str>,
) -> String {
    if let Some(p) = override_prefix {
        let t = p.trim();
        if !t.is_empty() {
            return t.to_string();
        }
    }
    let origin = resolve_origin(base_url, default_api_url);
    if is_hosted_tollara_api_origin(&origin) {
        ECS_USAGE_PATH_PREFIX.to_string()
    } else {
        default_usage_prefix.to_string()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn hosted_prod_uses_ecs_gateway_prefix() {
        assert!(is_hosted_tollara_api_origin("https://api.tollara.ai"));
        assert_eq!(
            resolve_gateway_path_prefix(None, DEFAULT_API_URL, "/api", None),
            ECS_GATEWAY_PATH_PREFIX
        );
        assert_eq!(
            resolve_gateway_path_prefix(
                Some("http://host.docker.internal:8083"),
                DEFAULT_API_URL,
                "/api",
                None,
            ),
            "/api"
        );
    }

    const DEFAULT_API_URL: &str = "https://api.tollara.ai";
}
