/** Canonical Tollara HTTP header names (gateway-signed requests and signed responses). */
export const TollaraHeaders = {
  SIGNATURE: 'X-Tollara-Signature',
  TIMESTAMP: 'X-Tollara-Timestamp',
  USER_ID: 'X-Tollara-User-ID',
  PLAN: 'X-Tollara-Plan',
  ROLES: 'X-Tollara-Roles',
  QUOTA_REMAINING: 'X-Tollara-Quota-Remaining',
  SUBSCRIPTION_ACTIVE: 'X-Tollara-Subscription-Active',
  BILLING_MODEL: 'X-Tollara-Billing-Model',
  MEASUREMENT_TYPE: 'X-Tollara-Measurement-Type',
  UNIT_LABEL: 'X-Tollara-Unit-Label',
  /** Gateway HMAC user-context schema: `2` = v2 suffix (leading `2`, no quota segment). */
  SIGNING_VERSION: 'X-Tollara-Signing-Version',
} as const;

export type TollaraHeaderName = (typeof TollaraHeaders)[keyof typeof TollaraHeaders];
