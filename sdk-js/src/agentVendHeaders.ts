/** Canonical AgentVend HTTP header names (gateway-signed requests and signed responses). */
export const AgentVendHeaders = {
  SIGNATURE: 'X-AgentVend-Signature',
  TIMESTAMP: 'X-AgentVend-Timestamp',
  USER_ID: 'X-AgentVend-User-ID',
  PLAN: 'X-AgentVend-Plan',
  ROLES: 'X-AgentVend-Roles',
  QUOTA_REMAINING: 'X-AgentVend-Quota-Remaining',
  SUBSCRIPTION_ACTIVE: 'X-AgentVend-Subscription-Active',
  BILLING_MODEL: 'X-AgentVend-Billing-Model',
  MEASUREMENT_TYPE: 'X-AgentVend-Measurement-Type',
  UNIT_LABEL: 'X-AgentVend-Unit-Label',
  /** Gateway HMAC user-context schema: `2` = v2 suffix (leading `2`, no quota segment). */
  SIGNING_VERSION: 'X-AgentVend-Signing-Version',
} as const;

export type AgentVendHeaderName = (typeof AgentVendHeaders)[keyof typeof AgentVendHeaders];
