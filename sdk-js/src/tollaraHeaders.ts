/** Canonical Tollara HTTP header names (gateway-signed requests and signed responses). */
export const TollaraHeaders = {
  SIGNATURE: 'X-Tollara-Signature',
  TIMESTAMP: 'X-Tollara-Timestamp',
  USER_ID: 'X-Tollara-User-ID',
  /** @deprecated v1/v2 only; v3 uses SERVICE_PRODUCT_ID */
  PLAN: 'X-Tollara-Plan',
  SERVICE_PRODUCT_ID: 'X-Tollara-Service-Product-ID',
  ROLES: 'X-Tollara-Roles',
  /** @deprecated v1 only */
  QUOTA_REMAINING: 'X-Tollara-Quota-Remaining',
  /** @deprecated v1/v2 only; v3 uses SUBSCRIPTION_STATUS */
  SUBSCRIPTION_ACTIVE: 'X-Tollara-Subscription-Active',
  SUBSCRIPTION_STATUS: 'X-Tollara-Subscription-Status',
  BILLING_MODEL: 'X-Tollara-Billing-Model',
  MEASUREMENT_TYPE: 'X-Tollara-Measurement-Type',
  UNIT_LABEL: 'X-Tollara-Unit-Label',
  /** Gateway HMAC user-context schema: `2` = v2 suffix (leading `2`, no quota segment). */
  SIGNING_VERSION: 'X-Tollara-Signing-Version',
} as const;

export type TollaraHeaderName = (typeof TollaraHeaders)[keyof typeof TollaraHeaders];
