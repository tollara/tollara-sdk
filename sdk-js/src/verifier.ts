import { calculateHmac, constantTimeEquals } from './hmac';

export interface UserContext {
  userId: string | null;
  plan: string | null;
  roles: string[];
  quotaRemaining: number | null;
  subscriptionActive: boolean;
}

export interface VerifySignatureInput {
  signature: string;
  timestamp: string;
  payload: string | object | null;
  userId: string | null;
  plan: string | null;
  roles: string[];
  quotaRemaining: number | string | null;
}

/**
 * Build userContextString for HMAC (per spec: userId + plan + roles.join(',') + quotaRemaining).
 */
function buildUserContextString(
  userId: string | null,
  plan: string | null,
  roles: string[],
  quotaRemaining: number | string | null
): string {
  const u = userId ?? '';
  const p = plan ?? '';
  const r = roles?.length ? roles.join(',') : '';
  const q = quotaRemaining != null ? String(quotaRemaining) : '';
  return u + p + r + q;
}

/**
 * Verifies HMAC on an inbound gateway request.
 * Canonical string: payload + timestamp + userContextString.
 */
export function verifySignature(
  agentSecret: string,
  input: VerifySignatureInput
): boolean {
  const { signature, timestamp, payload, userId, plan, roles, quotaRemaining } = input;
  if (!signature || !timestamp || !agentSecret) return false;
  try {
    const payloadString =
      payload == null ? '' : typeof payload === 'string' ? payload : JSON.stringify(payload);
    const userContextString = buildUserContextString(userId, plan, roles, quotaRemaining);
    const dataToSign = payloadString + timestamp + userContextString;
    const expectedSignature = calculateHmac(dataToSign, agentSecret);
    return constantTimeEquals(expectedSignature, signature);
  } catch {
    return false;
  }
}

/**
 * Parses X-Marketplace-* headers into UserContext.
 */
export function getUserContext(headers: {
  'X-Marketplace-User-ID'?: string | null;
  'X-Marketplace-Plan'?: string | null;
  'X-Marketplace-Roles'?: string | null;
  'X-Marketplace-Quota-Remaining'?: string | null;
  'X-Marketplace-Subscription-Active'?: string | null;
}): UserContext {
  const rolesHeader = headers['X-Marketplace-Roles'];
  const roles = rolesHeader ? rolesHeader.split(',').map((s) => s.trim()).filter(Boolean) : [];
  let quotaRemaining: number | null = null;
  const q = headers['X-Marketplace-Quota-Remaining'];
  if (q != null && q !== '') {
    const n = Number(q);
    if (!Number.isNaN(n)) quotaRemaining = n;
  }
  const sub = headers['X-Marketplace-Subscription-Active'];
  const subscriptionActive = sub != null && (sub === 'true' || sub === '1');
  return {
    userId: headers['X-Marketplace-User-ID'] ?? null,
    plan: headers['X-Marketplace-Plan'] ?? null,
    roles,
    quotaRemaining,
    subscriptionActive,
  };
}
