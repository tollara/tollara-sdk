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

const INVOKE_ELIGIBLE: &[&str] = &["ACTIVE", "TRIAL", "CANCELLING", "CANCELLING_PENDING"];

/// Returns true when `subscription_status` is invoke-eligible.
pub fn grants_access(subscription_status: Option<&str>) -> bool {
    match subscription_status.map(str::trim).filter(|s| !s.is_empty()) {
        None => false,
        Some(s) => INVOKE_ELIGIBLE.contains(&s.to_ascii_uppercase()),
    }
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct UserContext {
    pub user_id: Option<String>,
    pub service_product_id: Option<String>,
    pub roles: Vec<String>,
    pub subscription_status: Option<String>,
    pub billing_model_type: Option<String>,
    pub measurement_type: Option<String>,
    pub unit_label: Option<String>,
    /// Deprecated v1/v2.
    pub plan: Option<String>,
    pub quota_remaining: Option<f64>,
    pub subscription_active: bool,
}

/// User fields in inbound HMAC `user_context_string`.
#[derive(Debug, Clone)]
pub struct SignedUserContext {
    pub user_id: Option<String>,
    pub service_product_id: Option<String>,
    pub roles: Vec<String>,
    pub subscription_status: Option<String>,
    pub billing_model_type: Option<String>,
    pub measurement_type: Option<String>,
    pub unit_label: Option<String>,
    /// Deprecated v1/v2.
    pub plan: Option<String>,
    pub quota_remaining: Option<f64>,
    pub subscription_active: bool,
}

#[derive(Debug, Clone)]
pub struct InboundHmacVerify {
    pub signature: String,
    pub timestamp: String,
    pub payload: String,
    pub signed: SignedUserContext,
    pub signing_version: Option<String>,
}

fn format_quota(q: Option<f64>) -> String {
    match q {
        None => String::new(),
        Some(x) if x.fract() == 0.0 => format!("{}", x as i64),
        Some(x) => x.to_string(),
    }
}

fn str_or_empty(s: Option<&str>) -> &str {
    s.unwrap_or("")
}

/// Builds gateway inbound HMAC suffix v1 (after `payload` + `timestamp`).
pub fn build_gateway_user_context_string(s: &SignedUserContext) -> String {
    let u = str_or_empty(s.user_id.as_deref());
    let p = str_or_empty(s.plan.as_deref());
    let r = s.roles.join(",");
    let q = format_quota(s.quota_remaining);
    let sub = if s.subscription_active { "true" } else { "false" };
    let b = str_or_empty(s.billing_model_type.as_deref());
    let m = str_or_empty(s.measurement_type.as_deref());
    let ul = str_or_empty(s.unit_label.as_deref());
    format!("{u}{p}{r}{q}{sub}{b}{m}{ul}")
}

pub fn build_gateway_user_context_string_v2(s: &SignedUserContext) -> String {
    let u = str_or_empty(s.user_id.as_deref());
    let p = str_or_empty(s.plan.as_deref());
    let r = s.roles.join(",");
    let sub = if s.subscription_active { "true" } else { "false" };
    let b = str_or_empty(s.billing_model_type.as_deref());
    let m = str_or_empty(s.measurement_type.as_deref());
    let ul = str_or_empty(s.unit_label.as_deref());
    format!("2{u}{p}{r}{sub}{b}{m}{ul}")
}

/// Builds gateway inbound HMAC suffix v3 (leading `"3"`, serviceProductId, subscriptionStatus).
pub fn build_gateway_user_context_string_v3(s: &SignedUserContext) -> String {
    let u = str_or_empty(s.user_id.as_deref());
    let sp = str_or_empty(s.service_product_id.as_deref());
    let r = s.roles.join(",");
    let ss = str_or_empty(s.subscription_status.as_deref());
    let b = str_or_empty(s.billing_model_type.as_deref());
    let m = str_or_empty(s.measurement_type.as_deref());
    let ul = str_or_empty(s.unit_label.as_deref());
    format!("3{u}{sp}{r}{ss}{b}{m}{ul}")
}

/// Verify inbound gateway request HMAC. Canonical: payload + timestamp + user_context_string.
pub fn verify_inbound_hmac(agent_secret: &str, req: &InboundHmacVerify) -> bool {
    if req.signature.is_empty() || req.timestamp.is_empty() || agent_secret.is_empty() {
        return false;
    }
    let user_context_string = match req.signing_version.as_deref() {
        Some(headers::SIGNING_VERSION_V3) => build_gateway_user_context_string_v3(&req.signed),
        Some("2") => build_gateway_user_context_string_v2(&req.signed),
        _ => build_gateway_user_context_string(&req.signed),
    };
    let data_to_sign = format!("{}{}{}", req.payload, req.timestamp, user_context_string);
    let expected = calculate_hmac(&data_to_sign, agent_secret);
    constant_time_equals(&expected, &req.signature)
}

fn header_get_ci(headers: &HashMap<String, String>, canonical: &str) -> Option<String> {
    headers
        .iter()
        .find(|(k, _)| k.eq_ignore_ascii_case(canonical))
        .map(|(_, v)| v.clone())
}

fn parse_subscription_active(raw: Option<&str>) -> bool {
    match raw {
        None => false,
        Some(s) => {
            let t = s.trim();
            t.eq_ignore_ascii_case("true") || t == "1"
        }
    }
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
    let signed = parse_signed_user_context(headers_map);
    let req = InboundHmacVerify {
        signature,
        timestamp,
        payload: payload.to_string(),
        signed,
        signing_version: header_get_ci(headers_map, headers::SIGNING_VERSION),
    };
    verify_inbound_hmac(agent_secret, &req)
}

fn parse_signed_user_context(headers_map: &HashMap<String, String>) -> SignedUserContext {
    let roles_s = header_get_ci(headers_map, headers::ROLES).unwrap_or_default();
    let roles: Vec<String> = roles_s
        .split(',')
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())
        .collect();
    let quota = header_get_ci(headers_map, headers::QUOTA_REMAINING)
        .and_then(|s| s.parse::<f64>().ok());
    let sub = header_get_ci(headers_map, headers::SUBSCRIPTION_ACTIVE);
    let subscription_active = parse_subscription_active(sub.as_deref());
    let bm = header_get_ci(headers_map, headers::BILLING_MODEL).filter(|s| !s.is_empty());
    let mt = header_get_ci(headers_map, headers::MEASUREMENT_TYPE).filter(|s| !s.is_empty());
    let ul = header_get_ci(headers_map, headers::UNIT_LABEL).filter(|s| !s.is_empty());
    SignedUserContext {
        user_id: header_get_ci(headers_map, headers::USER_ID),
        service_product_id: header_get_ci(headers_map, headers::SERVICE_PRODUCT_ID),
        roles,
        subscription_status: header_get_ci(headers_map, headers::SUBSCRIPTION_STATUS),
        billing_model_type: bm,
        measurement_type: mt,
        unit_label: ul,
        plan: header_get_ci(headers_map, headers::PLAN),
        quota_remaining: quota,
        subscription_active,
    }
}

/// Verifies inbound HMAC; returns [`Some`] user context if valid, else [`None`].
pub fn verify_signature_from_headers_and_get_user_context(
    agent_secret: &str,
    headers_map: &HashMap<String, String>,
    payload: &str,
) -> Option<UserContext> {
    if !verify_signature_from_headers(agent_secret, headers_map, payload) {
        return None;
    }
    Some(parse_user_context(headers_map))
}

pub fn parse_user_context(headers_map: &HashMap<String, String>) -> UserContext {
    let signed = parse_signed_user_context(headers_map);
    UserContext {
        user_id: signed.user_id,
        service_product_id: signed.service_product_id,
        roles: signed.roles,
        subscription_status: signed.subscription_status,
        billing_model_type: signed.billing_model_type,
        measurement_type: signed.measurement_type,
        unit_label: signed.unit_label,
        plan: signed.plan,
        quota_remaining: signed.quota_remaining,
        subscription_active: signed.subscription_active,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn build_v3_all_fields_present_golden_string() {
        let s = SignedUserContext {
            user_id: Some("sub-ext-id".into()),
            service_product_id: Some("prod-uuid-1".into()),
            roles: vec!["roleA".into(), "roleB".into()],
            subscription_status: Some("ACTIVE".into()),
            billing_model_type: Some("SUBSCRIPTION".into()),
            measurement_type: Some("PER_REQUEST".into()),
            unit_label: Some("request".into()),
            plan: None,
            quota_remaining: None,
            subscription_active: false,
        };
        assert_eq!(
            build_gateway_user_context_string_v3(&s),
            "3sub-ext-idprod-uuid-1roleA,roleBACTIVESUBSCRIPTIONPER_REQUESTrequest"
        );
    }

    #[test]
    fn build_v3_empty_roles_golden_string() {
        let s = SignedUserContext {
            user_id: Some("user-1".into()),
            service_product_id: Some("prod-1".into()),
            roles: vec![],
            subscription_status: Some("TRIAL".into()),
            billing_model_type: None,
            measurement_type: None,
            unit_label: None,
            plan: None,
            quota_remaining: None,
            subscription_active: false,
        };
        assert_eq!(build_gateway_user_context_string_v3(&s), "3user-1prod-1TRIAL");
    }

    #[test]
    fn build_v3_billing_fields_absent_golden_string() {
        let s = SignedUserContext {
            user_id: Some("owner-id".into()),
            service_product_id: Some(String::new()),
            roles: vec![],
            subscription_status: Some("ACTIVE".into()),
            billing_model_type: None,
            measurement_type: None,
            unit_label: None,
            plan: None,
            quota_remaining: None,
            subscription_active: false,
        };
        assert_eq!(build_gateway_user_context_string_v3(&s), "3owner-idACTIVE");
    }

    #[test]
    fn build_v3_non_access_status_golden_string() {
        let s = SignedUserContext {
            user_id: Some("user-x".into()),
            service_product_id: Some("prod-x".into()),
            roles: vec!["r1".into()],
            subscription_status: Some("EXPIRED".into()),
            billing_model_type: Some("PREPAID".into()),
            measurement_type: Some("PER_REQUEST".into()),
            unit_label: Some("request".into()),
            plan: None,
            quota_remaining: None,
            subscription_active: false,
        };
        assert_eq!(
            build_gateway_user_context_string_v3(&s),
            "3user-xprod-xr1EXPIREDPREPAIDPER_REQUESTrequest"
        );
    }

    #[test]
    fn grants_access_eligible_and_ineligible() {
        assert!(grants_access(Some("ACTIVE")));
        assert!(grants_access(Some("CANCELLING_PENDING")));
        assert!(!grants_access(Some("EXPIRED")));
        assert!(!grants_access(None));
    }

    #[test]
    fn verify_inbound_hmac_v3() {
        let secret = "my-agent-secret";
        let payload = "";
        let ts = "1700000000";
        let signed = SignedUserContext {
            user_id: Some("user1".into()),
            service_product_id: Some("prod-1".into()),
            roles: vec!["role1".into(), "role2".into()],
            subscription_status: Some("ACTIVE".into()),
            billing_model_type: Some("SUBSCRIPTION".into()),
            measurement_type: Some("PER_REQUEST".into()),
            unit_label: Some("request".into()),
            plan: None,
            quota_remaining: None,
            subscription_active: false,
        };
        let ucs = build_gateway_user_context_string_v3(&signed);
        let sig = calculate_hmac(&format!("{}{}{}", payload, ts, ucs), secret);
        let req = InboundHmacVerify {
            signature: sig,
            timestamp: ts.to_string(),
            payload: payload.to_string(),
            signed,
            signing_version: Some(headers::SIGNING_VERSION_V3.into()),
        };
        assert!(verify_inbound_hmac(secret, &req));
    }

    #[test]
    fn verify_inbound_hmac_extended_vector() {
        let secret = "my-agent-secret";
        let payload = "";
        let ts = "1700000000";
        let signed = SignedUserContext {
            user_id: Some("user1".into()),
            plan: Some("plan1".into()),
            roles: vec!["role1".into(), "role2".into()],
            quota_remaining: Some(10.0),
            subscription_active: false,
            billing_model_type: None,
            measurement_type: None,
            unit_label: None,
            service_product_id: None,
            subscription_status: None,
        };
        let ucs = build_gateway_user_context_string(&signed);
        let sig = calculate_hmac(&format!("{}{}{}", payload, ts, ucs), secret);
        let req = InboundHmacVerify {
            signature: sig,
            timestamp: ts.to_string(),
            payload: payload.to_string(),
            signed,
            signing_version: None,
        };
        assert!(verify_inbound_hmac(secret, &req));
    }

    #[test]
    fn verify_signature_from_headers_lowercase_keys() {
        let secret = "my-agent-secret";
        let mut m = HashMap::new();
        let payload = "";
        let ts = "1700000000";
        let signed = SignedUserContext {
            user_id: Some("user1".into()),
            plan: Some("plan1".into()),
            roles: vec!["role1".into(), "role2".into()],
            quota_remaining: Some(10.0),
            subscription_active: false,
            billing_model_type: None,
            measurement_type: None,
            unit_label: None,
            service_product_id: None,
            subscription_status: None,
        };
        let ucs = build_gateway_user_context_string(&signed);
        let sig = calculate_hmac(&format!("{}{}{}", payload, ts, ucs), secret);
        m.insert("x-tollara-signature".into(), sig);
        m.insert("x-tollara-timestamp".into(), ts.into());
        m.insert("x-tollara-user-id".into(), "user1".into());
        m.insert("x-tollara-plan".into(), "plan1".into());
        m.insert("x-tollara-roles".into(), "role1,role2".into());
        m.insert("x-tollara-quota-remaining".into(), "10".into());
        m.insert("x-tollara-subscription-active".into(), "false".into());
        assert!(verify_signature_from_headers(secret, &m, payload));
    }

    #[test]
    fn subscriber_with_billing_matches() {
        let secret = "test-agent-secret";
        let signed = SignedUserContext {
            user_id: Some("sub-user".into()),
            plan: Some("basic".into()),
            roles: vec!["roleA".into(), "roleB".into()],
            quota_remaining: Some(50.0),
            subscription_active: true,
            billing_model_type: Some("SUBSCRIPTION".into()),
            measurement_type: Some("PER_REQUEST".into()),
            unit_label: Some("request".into()),
            service_product_id: None,
            subscription_status: None,
        };
        let payload = "";
        let ts = "1710000000";
        let ucs = build_gateway_user_context_string(&signed);
        let sig = calculate_hmac(&format!("{}{}{}", payload, ts, ucs), secret);
        let req = InboundHmacVerify {
            signature: sig,
            timestamp: ts.into(),
            payload: payload.into(),
            signed,
            signing_version: None,
        };
        assert!(verify_inbound_hmac(secret, &req));
    }

    #[test]
    fn verify_signature_from_headers_and_get_user_context_ok() {
        let secret = "my-agent-secret";
        let mut m = HashMap::new();
        let payload = "";
        let ts = "1700000000";
        let signed = SignedUserContext {
            user_id: Some("user1".into()),
            plan: Some("plan1".into()),
            roles: vec!["role1".into(), "role2".into()],
            quota_remaining: Some(10.0),
            subscription_active: false,
            billing_model_type: None,
            measurement_type: None,
            unit_label: None,
            service_product_id: None,
            subscription_status: None,
        };
        let ucs = build_gateway_user_context_string(&signed);
        let sig = calculate_hmac(&format!("{}{}{}", payload, ts, ucs), secret);
        m.insert("X-Tollara-Signature".into(), sig);
        m.insert("X-Tollara-Timestamp".into(), ts.into());
        m.insert("X-Tollara-User-ID".into(), "user1".into());
        m.insert("X-Tollara-Plan".into(), "plan1".into());
        m.insert("X-Tollara-Roles".into(), "role1,role2".into());
        m.insert("X-Tollara-Quota-Remaining".into(), "10".into());
        m.insert("X-Tollara-Subscription-Active".into(), "false".into());
        let ctx = verify_signature_from_headers_and_get_user_context(secret, &m, payload).expect("context");
        assert_eq!(ctx.user_id.as_deref(), Some("user1"));
    }

    #[test]
    fn verify_signature_from_headers_and_get_user_context_invalid() {
        let mut m = HashMap::new();
        m.insert("X-Tollara-Signature".into(), "bad".into());
        m.insert("X-Tollara-Timestamp".into(), "1700000000".into());
        assert!(verify_signature_from_headers_and_get_user_context("my-agent-secret", &m, "").is_none());
    }
}
