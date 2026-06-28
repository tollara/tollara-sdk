//! Canonical Tollara HTTP header names.

pub const SIGNATURE: &str = "X-Tollara-Signature";
pub const TIMESTAMP: &str = "X-Tollara-Timestamp";
pub const USER_ID: &str = "X-Tollara-User-ID";
pub const SERVICE_PRODUCT_ID: &str = "X-Tollara-Service-Product-ID";
pub const ROLES: &str = "X-Tollara-Roles";
pub const SUBSCRIPTION_STATUS: &str = "X-Tollara-Subscription-Status";
pub const BILLING_MODEL: &str = "X-Tollara-Billing-Model";
pub const MEASUREMENT_TYPE: &str = "X-Tollara-Measurement-Type";
pub const UNIT_LABEL: &str = "X-Tollara-Unit-Label";
pub const SIGNING_VERSION: &str = "X-Tollara-Signing-Version";
pub const SIGNING_VERSION_V3: &str = "3";

/// Deprecated v1/v2 headers.
pub const PLAN: &str = "X-Tollara-Plan";
pub const QUOTA_REMAINING: &str = "X-Tollara-Quota-Remaining";
pub const SUBSCRIPTION_ACTIVE: &str = "X-Tollara-Subscription-Active";
