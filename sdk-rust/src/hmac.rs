use base64::{engine::general_purpose::STANDARD as BASE64, Engine};
use hmac::{Hmac, Mac};
use sha2::Sha256;
use std::collections::HashMap;

use crate::headers;

type HmacSha256 = Hmac<Sha256>;

pub fn calculate_hmac(data: &str, key: &str) -> String {
    let mut mac = HmacSha256::new_from_slice(key.as_bytes()).expect("HMAC key length");
    mac.update(data.as_bytes());
    BASE64.encode(mac.finalize().into_bytes())
}

pub fn calculate_hmac_with_timestamp(body_string: &str, timestamp: &str, key: &str) -> String {
    calculate_hmac(&format!("{}{}", body_string, timestamp), key)
}

pub fn constant_time_equals(a: &str, b: &str) -> bool {
    use subtle::ConstantTimeEq;
    let aa = a.as_bytes();
    let bb = b.as_bytes();
    aa.len() == bb.len() && aa.ct_eq(bb).into()
}

pub fn validate_hmac_signature(signature: &str, payload_string: &str, key: &str) -> bool {
    if signature.is_empty() || key.is_empty() {
        return false;
    }
    let expected = calculate_hmac(payload_string, key);
    constant_time_equals(&expected, signature)
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct UserContext {
    pub user_id: Option<String>,
    pub plan: Option<String>,
    pub roles: Vec<String>,
    pub quota_remaining: Option<f64>,
    pub subscription_active: bool,
}

/// User fields in inbound HMAC `user_context_string` (hmac-spec.md).
#[derive(Debug, Clone)]
pub struct SignedUserContext {
    pub user_id: Option<String>,
    pub plan: Option<String>,
    pub roles: Vec<String>,
    pub quota_remaining: Option<f64>,
}

#[derive(Debug, Clone)]
pub struct InboundHmacVerify {
    pub signature: String,
    pub timestamp: String,
    pub payload: String,
    pub signed: SignedUserContext,
}

fn format_quota(q: Option<f64>) -> String {
    match q {
        None => String::new(),
        Some(x) if x.fract() == 0.0 => format!("{}", x as i64),
        Some(x) => x.to_string(),
    }
}

/// Verify inbound gateway request HMAC. Canonical: payload + timestamp + user_context_string.
pub fn verify_signature(
    agent_secret: &str,
    signature: &str,
    timestamp: &str,
    payload: &str,
    user_id: &str,
    plan: &str,
    roles: &[String],
    quota_remaining: &str,
) -> bool {
    if signature.is_empty() || timestamp.is_empty() || agent_secret.is_empty() {
        return false;
    }
    let user_context_string = format!(
        "{}{}{}{}",
        user_id,
        plan,
        roles.join(","),
        quota_remaining
    );
    let data_to_sign = format!("{}{}{}", payload, timestamp, user_context_string);
    let expected = calculate_hmac(&data_to_sign, agent_secret);
    constant_time_equals(&expected, signature)
}

pub fn verify_inbound_hmac(agent_secret: &str, req: &InboundHmacVerify) -> bool {
    let q = format_quota(req.signed.quota_remaining);
    verify_signature(
        agent_secret,
        &req.signature,
        &req.timestamp,
        &req.payload,
        req.signed.user_id.as_deref().unwrap_or(""),
        req.signed.plan.as_deref().unwrap_or(""),
        &req.signed.roles,
        &q,
    )
}

fn header_get_ci(headers: &HashMap<String, String>, canonical: &str) -> Option<String> {
    headers
        .iter()
        .find(|(k, _)| k.eq_ignore_ascii_case(canonical))
        .map(|(_, v)| v.clone())
}

pub fn verify_signature_from_headers(
    agent_secret: &str,
    headers_map: &HashMap<String, String>,
    payload: &str,
) -> bool {
    let signature = match header_get_ci(headers_map, headers::SIGNATURE) {
        Some(s) if !s.is_empty() => s,
        _ => return false,
    };
    let timestamp = match header_get_ci(headers_map, headers::TIMESTAMP) {
        Some(s) if !s.is_empty() => s,
        _ => return false,
    };
    let roles_s = header_get_ci(headers_map, headers::ROLES).unwrap_or_default();
    let roles: Vec<String> = roles_s
        .split(',')
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())
        .collect();
    let quota = header_get_ci(headers_map, headers::QUOTA_REMAINING)
        .and_then(|s| s.parse::<f64>().ok());
    let signed = SignedUserContext {
        user_id: header_get_ci(headers_map, headers::USER_ID),
        plan: header_get_ci(headers_map, headers::PLAN),
        roles,
        quota_remaining: quota,
    };
    let req = InboundHmacVerify {
        signature,
        timestamp,
        payload: payload.to_string(),
        signed,
    };
    verify_inbound_hmac(agent_secret, &req)
}

pub fn parse_user_context(headers_map: &HashMap<String, String>) -> UserContext {
    let roles_s = header_get_ci(headers_map, headers::ROLES).unwrap_or_default();
    let roles: Vec<String> = roles_s
        .split(',')
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())
        .collect();
    let quota_remaining = header_get_ci(headers_map, headers::QUOTA_REMAINING)
        .and_then(|s| s.parse::<f64>().ok());
    let sub = header_get_ci(headers_map, headers::SUBSCRIPTION_ACTIVE);
    let subscription_active = sub.as_deref() == Some("true") || sub.as_deref() == Some("1");
    UserContext {
        user_id: header_get_ci(headers_map, headers::USER_ID),
        plan: header_get_ci(headers_map, headers::PLAN),
        roles,
        quota_remaining,
        subscription_active,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn verify_inbound_hmac_hmac_spec_vector() {
        let secret = "my-agent-secret";
        let payload = "";
        let ts = "1700000000";
        let ucs = "user1plan1role1,role210";
        let sig = calculate_hmac(&format!("{}{}{}", payload, ts, ucs), secret);
        let req = InboundHmacVerify {
            signature: sig,
            timestamp: ts.to_string(),
            payload: payload.to_string(),
            signed: SignedUserContext {
                user_id: Some("user1".into()),
                plan: Some("plan1".into()),
                roles: vec!["role1".into(), "role2".into()],
                quota_remaining: Some(10.0),
            },
        };
        assert!(verify_inbound_hmac(secret, &req));
    }

    #[test]
    fn verify_signature_from_headers_lowercase_keys() {
        let secret = "my-agent-secret";
        let mut m = HashMap::new();
        let payload = "";
        let ts = "1700000000";
        let sig = calculate_hmac(
            &format!("{}{}{}", payload, ts, "user1plan1role1,role210"),
            secret,
        );
        m.insert("x-agentvend-signature".into(), sig);
        m.insert("x-agentvend-timestamp".into(), ts.into());
        m.insert("x-agentvend-user-id".into(), "user1".into());
        m.insert("x-agentvend-plan".into(), "plan1".into());
        m.insert("x-agentvend-roles".into(), "role1,role2".into());
        m.insert("x-agentvend-quota-remaining".into(), "10".into());
        assert!(verify_signature_from_headers(secret, &m, payload));
    }
}
