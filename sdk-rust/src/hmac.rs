use base64::{engine::general_purpose::STANDARD as BASE64, Engine};
use hmac::{Hmac, Mac};
use sha2::Sha256;

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
