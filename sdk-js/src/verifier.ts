import { TollaraHeaders } from './tollaraHeaders';
import { calculateHmac, constantTimeEquals } from './hmac';

export interface UserContext {
  userId: string | null;
  plan: string | null;
  roles: string[];
  quotaRemaining: number | null;
  subscriptionActive: boolean;
  billingModelType: string | null;
  measurementType: string | null;
  unitLabel: string | null;
}

export interface VerifySignatureInput {
  signature: string;
  timestamp: string;
  payload: string | object | null;
  userId: string | null;
  plan: string | null;
  roles: string[];
  quotaRemaining: number | string | null;
  /** Must match X-Tollara-Subscription-Active for verification. */
  subscriptionActive: boolean;
  billingModelType?: string | null;
  measurementType?: string | null;
  unitLabel?: string | null;
  /** When `2`, uses HMAC user-context v2 (see TollaraHeaders.SIGNING_VERSION). */
  signingVersion?: string | null;
}

/** User fields that participate in inbound HMAC userContextString (docs/hmac-spec.md). */
export interface SignedUserContext {
  userId: string | null;
  plan: string | null;
  roles: string[];
  quotaRemaining: number | string | null;
  subscriptionActive: boolean;
  billingModelType?: string | null;
  measurementType?: string | null;
  unitLabel?: string | null;
}

export interface InboundHmacRequest {
  signature: string;
  timestamp: string;
  payload: string | object | null;
  signedUserContext: SignedUserContext;
  signingVersion?: string | null;
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

function parseSubscriptionActive(raw: string | null): boolean {
  if (raw == null || raw === '') return false;
  const t = raw.trim();
  return t === 'true' || t === '1';
}

/**
 * Gateway inbound HMAC suffix: userId + plan + rolesCsv + quota + subscriptionActive + billing + measurement + unit.
 */
export function buildGatewayUserContextString(
  userId: string | null,
  plan: string | null,
  roles: string[],
  quotaRemaining: number | string | null,
  subscriptionActive: boolean,
  billingModelType: string | null,
  measurementType: string | null,
  unitLabel: string | null
): string {
  const u = userId ?? '';
  const p = plan ?? '';
  const r = roles?.length ? roles.join(',') : '';
  const q = quotaRemaining != null && quotaRemaining !== '' ? String(quotaRemaining) : '';
  const sub = subscriptionActive ? 'true' : 'false';
  const b = billingModelType ?? '';
  const m = measurementType ?? '';
  const ul = unitLabel ?? '';
  return u + p + r + q + sub + b + m + ul;
}

/** Gateway HMAC user-context v2: leading `2`, then userId, plan, roles, subscription, billing fields (no quota). */
export function buildGatewayUserContextStringV2(
  userId: string | null,
  plan: string | null,
  roles: string[],
  subscriptionActive: boolean,
  billingModelType: string | null,
  measurementType: string | null,
  unitLabel: string | null
): string {
  const u = userId ?? '';
  const p = plan ?? '';
  const r = roles?.length ? roles.join(',') : '';
  const sub = subscriptionActive ? 'true' : 'false';
  const b = billingModelType ?? '';
  const m = measurementType ?? '';
  const ul = unitLabel ?? '';
  return '2' + u + p + r + sub + b + m + ul;
}

/**
 * Verifies HMAC on an inbound gateway request.
 * Canonical string: payload + timestamp + userContextString (see docs/hmac-spec.md).
 */
export function verifySignature(serviceSecret: string, input: VerifySignatureInput): boolean {
  const {
    signature,
    timestamp,
    payload,
    userId,
    plan,
    roles,
    quotaRemaining,
    subscriptionActive,
    billingModelType,
    measurementType,
    unitLabel,
    signingVersion,
  } = input;
  if (!signature || !timestamp || !serviceSecret) return false;
  try {
    const payloadString =
      payload == null ? '' : typeof payload === 'string' ? payload : JSON.stringify(payload);
    const userContextString =
      signingVersion === '2'
        ? buildGatewayUserContextStringV2(
            userId,
            plan,
            roles ?? [],
            subscriptionActive,
            billingModelType ?? null,
            measurementType ?? null,
            unitLabel ?? null
          )
        : buildGatewayUserContextString(
            userId,
            plan,
            roles ?? [],
            quotaRemaining,
            subscriptionActive,
            billingModelType ?? null,
            measurementType ?? null,
            unitLabel ?? null
          );
    const dataToSign = payloadString + timestamp + userContextString;
    const expectedSignature = calculateHmac(dataToSign, serviceSecret);
    return constantTimeEquals(expectedSignature, signature);
  } catch {
    return false;
  }
}

/**
 * Verifies inbound HMAC using {@link InboundHmacRequest} (preferred typed entry point).
 */
export function verifyInboundHmac(serviceSecret: string, request: InboundHmacRequest): boolean {
  const s = request.signedUserContext;
  return verifySignature(serviceSecret, {
    signature: request.signature,
    timestamp: request.timestamp,
    payload: request.payload,
    userId: s.userId,
    plan: s.plan,
    roles: s.roles ?? [],
    quotaRemaining: s.quotaRemaining,
    subscriptionActive: s.subscriptionActive,
    billingModelType: s.billingModelType,
    measurementType: s.measurementType,
    unitLabel: s.unitLabel,
    signingVersion: request.signingVersion,
  });
}

/**
 * Verifies inbound HMAC from a case-insensitive header bag and payload.
 */
export function verifySignatureFromHeaders(
  serviceSecret: string,
  headers: HeaderBag,
  payload: string | object | null
): boolean {
  const signature = headerGet(headers, TollaraHeaders.SIGNATURE);
  const timestamp = headerGet(headers, TollaraHeaders.TIMESTAMP);
  if (!signature || !timestamp) return false;
  const rolesHeader = headerGet(headers, TollaraHeaders.ROLES);
  const roles = rolesHeader ? rolesHeader.split(',').map((x) => x.trim()).filter(Boolean) : [];
  let quotaRemaining: number | string | null = headerGet(headers, TollaraHeaders.QUOTA_REMAINING);
  if (quotaRemaining != null && quotaRemaining !== '') {
    const n = Number(quotaRemaining);
    if (!Number.isNaN(n)) quotaRemaining = n;
  } else {
    quotaRemaining = null;
  }
  const subRaw = headerGet(headers, TollaraHeaders.SUBSCRIPTION_ACTIVE);
  const subscriptionActive = parseSubscriptionActive(subRaw);
  const billing = headerGet(headers, TollaraHeaders.BILLING_MODEL);
  const measurement = headerGet(headers, TollaraHeaders.MEASUREMENT_TYPE);
  const unit = headerGet(headers, TollaraHeaders.UNIT_LABEL);
  const signedUserContext: SignedUserContext = {
    userId: headerGet(headers, TollaraHeaders.USER_ID),
    plan: headerGet(headers, TollaraHeaders.PLAN),
    roles,
    quotaRemaining,
    subscriptionActive,
    billingModelType: billing && billing !== '' ? billing : null,
    measurementType: measurement && measurement !== '' ? measurement : null,
    unitLabel: unit && unit !== '' ? unit : null,
  };
  const signingVersion = headerGet(headers, TollaraHeaders.SIGNING_VERSION);
  return verifyInboundHmac(serviceSecret, {
    signature,
    timestamp,
    payload,
    signedUserContext,
    signingVersion: signingVersion && signingVersion !== '' ? signingVersion : null,
  });
}

/**
 * Verifies inbound HMAC; if valid returns user context, else `null` (do not trust headers).
 * Parses `X-Tollara-*` headers into {@link UserContext} (case-insensitive header names).
 */
export function verifySignatureFromHeadersAndGetUserContext(
  serviceSecret: string,
  headers: HeaderBag,
  payload: string | object | null
): UserContext | null {
  if (!verifySignatureFromHeaders(serviceSecret, headers, payload)) return null;
  return getUserContext(headers);
}

export function getUserContext(headers: HeaderBag): UserContext {
  const rolesHeader = headerGet(headers, TollaraHeaders.ROLES);
  const roles = rolesHeader ? rolesHeader.split(',').map((s) => s.trim()).filter(Boolean) : [];
  let quotaRemaining: number | null = null;
  const q = headerGet(headers, TollaraHeaders.QUOTA_REMAINING);
  if (q != null && q !== '') {
    const n = Number(q);
    if (!Number.isNaN(n)) quotaRemaining = n;
  }
  const sub = headerGet(headers, TollaraHeaders.SUBSCRIPTION_ACTIVE);
  const subscriptionActive = parseSubscriptionActive(sub);
  const bm = headerGet(headers, TollaraHeaders.BILLING_MODEL);
  const mt = headerGet(headers, TollaraHeaders.MEASUREMENT_TYPE);
  const ul = headerGet(headers, TollaraHeaders.UNIT_LABEL);
  return {
    userId: headerGet(headers, TollaraHeaders.USER_ID),
    plan: headerGet(headers, TollaraHeaders.PLAN),
    roles,
    quotaRemaining,
    subscriptionActive,
    billingModelType: bm && bm !== '' ? bm : null,
    measurementType: mt && mt !== '' ? mt : null,
    unitLabel: ul && ul !== '' ? ul : null,
  };
}
