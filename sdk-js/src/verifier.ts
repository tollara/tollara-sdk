import { TollaraHeaders } from './tollaraHeaders';
import { calculateHmac, constantTimeEquals } from './hmac';
import { grantAccess } from './grantAccess';

export { grantAccess };

export interface UserContext {
  userId: string | null;
  /** v3: service product id; v1/v2 legacy: plan name in {@link plan}. */
  serviceProductId: string | null;
  /** @deprecated v1/v2 only */
  plan: string | null;
  roles: string[];
  /** @deprecated v1 only */
  quotaRemaining: number | null;
  /** v3: uppercase subscription status. */
  subscriptionStatus: string | null;
  /** @deprecated v1/v2 only */
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
  /** v3 */
  serviceProductId?: string | null;
  /** v1/v2 */
  plan?: string | null;
  roles: string[];
  /** v1 only */
  quotaRemaining?: number | string | null;
  /** v3 */
  subscriptionStatus?: string | null;
  /** v1/v2 */
  subscriptionActive?: boolean;
  billingModelType?: string | null;
  measurementType?: string | null;
  unitLabel?: string | null;
  /** `3` = v3; `2` = v2; absent = v1 */
  signingVersion?: string | null;
}

/** User fields that participate in inbound HMAC userContextString. */
export interface SignedUserContext {
  userId: string | null;
  serviceProductId?: string | null;
  plan?: string | null;
  roles: string[];
  quotaRemaining?: number | string | null;
  subscriptionStatus?: string | null;
  subscriptionActive?: boolean;
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
 * Gateway inbound HMAC suffix v1: userId + plan + rolesCsv + quota + subscriptionActive + billing fields.
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

/** Gateway HMAC user-context v3: leading `3`, serviceProductId, subscriptionStatus (see MAIN-SDK-API-SPEC §4). */
export function buildGatewayUserContextStringV3(
  userId: string | null,
  serviceProductId: string | null,
  roles: string[],
  subscriptionStatus: string | null,
  billingModelType: string | null,
  measurementType: string | null,
  unitLabel: string | null
): string {
  const u = userId ?? '';
  const sp = serviceProductId ?? '';
  const r = roles?.length ? roles.join(',') : '';
  const st = subscriptionStatus ?? '';
  const b = billingModelType ?? '';
  const m = measurementType ?? '';
  const ul = unitLabel ?? '';
  return '3' + u + sp + r + st + b + m + ul;
}

function buildUserContextString(input: VerifySignatureInput): string {
  const {
    userId,
    serviceProductId,
    plan,
    roles,
    quotaRemaining,
    subscriptionStatus,
    subscriptionActive,
    billingModelType,
    measurementType,
    unitLabel,
    signingVersion,
  } = input;
  if (signingVersion === '3') {
    return buildGatewayUserContextStringV3(
      userId,
      serviceProductId ?? null,
      roles ?? [],
      subscriptionStatus ?? null,
      billingModelType ?? null,
      measurementType ?? null,
      unitLabel ?? null
    );
  }
  if (signingVersion === '2') {
    return buildGatewayUserContextStringV2(
      userId,
      plan ?? null,
      roles ?? [],
      subscriptionActive ?? false,
      billingModelType ?? null,
      measurementType ?? null,
      unitLabel ?? null
    );
  }
  return buildGatewayUserContextString(
    userId,
    plan ?? null,
    roles ?? [],
    quotaRemaining ?? null,
    subscriptionActive ?? false,
    billingModelType ?? null,
    measurementType ?? null,
    unitLabel ?? null
  );
}

/**
 * Verifies HMAC on an inbound gateway request.
 * Canonical string: payload + timestamp + userContextString (see docs-sdk/MAIN-SDK-API-SPEC.md §4).
 */
export function verifySignature(serviceSecret: string, input: VerifySignatureInput): boolean {
  const { signature, timestamp, payload } = input;
  if (!signature || !timestamp || !serviceSecret) return false;
  try {
    const payloadString =
      payload == null ? '' : typeof payload === 'string' ? payload : JSON.stringify(payload);
    const userContextString = buildUserContextString(input);
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
    serviceProductId: s.serviceProductId,
    plan: s.plan,
    roles: s.roles ?? [],
    quotaRemaining: s.quotaRemaining,
    subscriptionStatus: s.subscriptionStatus,
    subscriptionActive: s.subscriptionActive,
    billingModelType: s.billingModelType,
    measurementType: s.measurementType,
    unitLabel: s.unitLabel,
    signingVersion: request.signingVersion,
  });
}

function parseSignedUserContextFromHeaders(headers: HeaderBag): SignedUserContext {
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
  return {
    userId: headerGet(headers, TollaraHeaders.USER_ID),
    serviceProductId: headerGet(headers, TollaraHeaders.SERVICE_PRODUCT_ID),
    plan: headerGet(headers, TollaraHeaders.PLAN),
    roles,
    quotaRemaining,
    subscriptionStatus: headerGet(headers, TollaraHeaders.SUBSCRIPTION_STATUS),
    subscriptionActive,
    billingModelType: billing && billing !== '' ? billing : null,
    measurementType: measurement && measurement !== '' ? measurement : null,
    unitLabel: unit && unit !== '' ? unit : null,
  };
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
  const signedUserContext = parseSignedUserContextFromHeaders(headers);
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
  const s = parseSignedUserContextFromHeaders(headers);
  return {
    userId: s.userId,
    serviceProductId: s.serviceProductId ?? null,
    plan: s.plan ?? null,
    roles: s.roles ?? [],
    quotaRemaining:
      typeof s.quotaRemaining === 'number'
        ? s.quotaRemaining
        : s.quotaRemaining != null && s.quotaRemaining !== ''
          ? Number(s.quotaRemaining)
          : null,
    subscriptionStatus: s.subscriptionStatus ?? null,
    subscriptionActive: s.subscriptionActive ?? false,
    billingModelType: s.billingModelType ?? null,
    measurementType: s.measurementType ?? null,
    unitLabel: s.unitLabel ?? null,
  };
}
