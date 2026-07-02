"""Canonical Tollara HTTP header names."""


class TollaraHeaders:
    SIGNATURE = "X-Tollara-Signature"
    TIMESTAMP = "X-Tollara-Timestamp"
    USER_ID = "X-Tollara-User-ID"
    SERVICE_PRODUCT_ID = "X-Tollara-Service-Product-ID"
    ROLES = "X-Tollara-Roles"
    SUBSCRIPTION_STATUS = "X-Tollara-Subscription-Status"
    BILLING_MODEL = "X-Tollara-Billing-Model"
    MEASUREMENT_TYPE = "X-Tollara-Measurement-Type"
    UNIT_LABEL = "X-Tollara-Unit-Label"

    # v1/v2 only; use SERVICE_PRODUCT_ID for signing version 3
    PLAN = "X-Tollara-Plan"
    # v1 only; omitted from v2/v3 HMAC material
    QUOTA_REMAINING = "X-Tollara-Quota-Remaining"
    # v1/v2 only; use SUBSCRIPTION_STATUS for signing version 3
    SUBSCRIPTION_ACTIVE = "X-Tollara-Subscription-Active"

    # Gateway HMAC user-context schema: 3 = v3; 2 = v2 (leading "2", no quota segment)
    SIGNING_VERSION = "X-Tollara-Signing-Version"
