import { AgentVendHeaders } from './agentVendHeaders';
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

/** User fields that participate in inbound HMAC userContextString (docs/hmac-spec.md). */
export interface SignedUserContext {
  userId: string | null;
  plan: string | null;
  roles: string[];
  quotaRemaining: number | string | null;
}

export interface InboundHmacRequest {
  signature: string;
  timestamp: string;
  payload: string | object | null;
  signedUserContext: SignedUserContext;
}

/** Loose header map (e.g. Node/Express lowercases keys). */
export type HeaderBag = Record<string, string | string[] | undefined | null>;

function headerGet(headers: HeaderBag, canonicalName: string): string | null {
  const target = canonicalName.toLowerCase();
  for (const [k, v] of Object.entries(headers)) {
    if (k.toLowerCase() === target) {
      if (v == null) return null;
      return Array.isArray(v) ? (v[0] ?? null) : String(v);
    }
  }
  return null;
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
export function verifySignature(agentSecret: string, input: VerifySignatureInput): boolean {
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
 * Verifies inbound HMAC using {@link InboundHmacRequest} (preferred typed entry point).
 */
export function verifyInboundHmac(agentSecret: string, request: InboundHmacRequest): boolean {
  const s = request.signedUserContext;
  return verifySignature(agentSecret, {
    signature: request.signature,
    timestamp: request.timestamp,
    payload: request.payload,
    userId: s.userId,
    plan: s.plan,
    roles: s.roles ?? [],
    quotaRemaining: s.quotaRemaining,
  });
}

/**
 * Verifies inbound HMAC from a case-insensitive header bag and payload.
 */
export function verifySignatureFromHeaders(
  agentSecret: string,
  headers: HeaderBag,
  payload: string | object | null
): boolean {
  const signature = headerGet(headers, AgentVendHeaders.SIGNATURE);
  const timestamp = headerGet(headers, AgentVendHeaders.TIMESTAMP);
  if (!signature || !timestamp) return false;
  const rolesHeader = headerGet(headers, AgentVendHeaders.ROLES);
  const roles = rolesHeader ? rolesHeader.split(',').map((x) => x.trim()).filter(Boolean) : [];
  let quotaRemaining: number | string | null = headerGet(headers, AgentVendHeaders.QUOTA_REMAINING);
  if (quotaRemaining != null && quotaRemaining !== '') {
    const n = Number(quotaRemaining);
    if (!Number.isNaN(n)) quotaRemaining = n;
  } else {
    quotaRemaining = null;
  }
  const signedUserContext: SignedUserContext = {
    userId: headerGet(headers, AgentVendHeaders.USER_ID),
    plan: headerGet(headers, AgentVendHeaders.PLAN),
    roles,
    quotaRemaining,
  };
  return verifyInboundHmac(agentSecret, {
    signature,
    timestamp,
    payload,
    signedUserContext,
  });
}

/**
 * Parses X-AgentVend-* headers into UserContext (case-insensitive header names).
 */
export function getUserContext(headers: HeaderBag): UserContext {
  const rolesHeader = headerGet(headers, AgentVendHeaders.ROLES);
  const roles = rolesHeader ? rolesHeader.split(',').map((s) => s.trim()).filter(Boolean) : [];
  let quotaRemaining: number | null = null;
  const q = headerGet(headers, AgentVendHeaders.QUOTA_REMAINING);
  if (q != null && q !== '') {
    const n = Number(q);
    if (!Number.isNaN(n)) quotaRemaining = n;
  }
  const sub = headerGet(headers, AgentVendHeaders.SUBSCRIPTION_ACTIVE);
  const subscriptionActive = sub != null && (sub === 'true' || sub === '1');
  return {
    userId: headerGet(headers, AgentVendHeaders.USER_ID),
    plan: headerGet(headers, AgentVendHeaders.PLAN),
    roles,
    quotaRemaining,
    subscriptionActive,
  };
}
