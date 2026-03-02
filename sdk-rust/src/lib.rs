//! Agent Hub SDK: HMAC verification, user context, validate key, report usage (see docs/hmac-spec.md).

mod hmac;

pub use hmac::{
    calculate_hmac, calculate_hmac_with_timestamp, constant_time_equals, validate_hmac_signature,
    verify_signature, UserContext,
};

#[cfg(feature = "http")]
pub mod validation_client;
#[cfg(feature = "http")]
pub mod usage_client;
