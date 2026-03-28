/** Canonical AgentVend HTTP header names (gateway-signed requests and signed responses). */
export const AgentVendHeaders = {
  SIGNATURE: 'X-AgentVend-Signature',
  TIMESTAMP: 'X-AgentVend-Timestamp',
  USER_ID: 'X-AgentVend-User-ID',
  PLAN: 'X-AgentVend-Plan',
  ROLES: 'X-AgentVend-Roles',
  QUOTA_REMAINING: 'X-AgentVend-Quota-Remaining',
  SUBSCRIPTION_ACTIVE: 'X-AgentVend-Subscription-Active',
} as const;

export type AgentVendHeaderName = (typeof AgentVendHeaders)[keyof typeof AgentVendHeaders];
