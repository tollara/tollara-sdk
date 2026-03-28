//! AgentVend SDK: HMAC verification, user context, validate key, report usage (see docs/hmac-spec.md).

mod headers;
mod hmac;

pub use headers::{
    BILLING_MODEL, MEASUREMENT_TYPE, PLAN, QUOTA_REMAINING, ROLES, SIGNATURE, SUBSCRIPTION_ACTIVE,
    TIMESTAMP, UNIT_LABEL, USER_ID,
};
pub use hmac::{
    build_gateway_user_context_string, calculate_hmac, calculate_hmac_with_timestamp,
    constant_time_equals, validate_hmac_signature, verify_inbound_hmac, verify_signature_from_headers,
    parse_user_context, InboundHmacVerify, SignedUserContext, UserContext,
};

#[cfg(feature = "http")]
pub mod gateway_client;
#[cfg(feature = "http")]
pub mod validation_client;
#[cfg(feature = "http")]
pub mod usage_client;
