import { TollaraHeaders } from './tollaraHeaders';
import { DEFAULT_API_URL } from './constants';
import { resolveCorePathPrefix } from './pathPrefixes';
import { calculateHmac, constantTimeEquals, validateHmacSignature } from './hmac';
import { joinUrl, resolveBaseUrl } from './urls';
import { grantAccess } from './grantAccess';
import { parseUsageBreakdown, type UsageBreakdown } from './usageBreakdown';

export { grantAccess };

export interface ServiceKeyValidationResult {
  userId: string | null;
  serviceId: string | null;
  /** Core service-key row id when present in the validate JSON body. */
  serviceKeyId: string | null;
  serviceProductId: string | null;
  roles: string[];
  subscriptionStatus: string | null;
  validationSchemaVersion: number;
  billingModelType: string | null;
  measurementType: string | null;
  unitLabel: string | null;
  /** Whether {@link subscriptionStatus} grants invoke access. */
  grantAccess: boolean;
}

export interface UsageEstimateResult {
  sufficientCredits: boolean;
  wouldExceedCap: boolean;
  wouldAllow: boolean;
  estimatedCost: number | null;
  billingModelType: string | null;
  measurementType: string | null;
  unitLabel: string | null;
  breakdown: UsageBreakdown | null;
  estimateSchemaVersion: number;
  timestamp: number;
  httpStatus: number;
}

/** Canonical failure codes for validateServiceKeyWithOutcome (see docs-sdk §2.1.1). */
export type ValidationFailureCode =
  | 'MISSING_KEY'
  | 'NETWORK'
  | 'HTTP_ERROR'
  | 'MISSING_SIGNATURE_HEADERS'
  | 'HMAC_MISMATCH'
  | 'INVALID_KEY'
  | 'PARSE_ERROR';

export type ServiceKeyValidationOutcome =
  | { ok: true; result: ServiceKeyValidationResult }
  | { ok: false; code: ValidationFailureCode; message?: string; httpStatus?: number };

interface ValidateResponseBody {
  valid?: boolean;
  serviceKeyId?: string;
  userId?: string;
  serviceId?: string;
  serviceProductId?: string;
  roles?: string[];
  subscriptionStatus?: string;
  validationSchemaVersion?: number;
  billingModelType?: string | null;
  measurementType?: string | null;
  unitLabel?: string | null;
  error?: string;
}

/** Unsigned 401/403 from Core: `{ valid: false, error?: string }`. */
function invalidKeyFromUnsignedErrorBody(
  responseText: string,
  httpStatus: number,
): ServiceKeyValidationOutcome | null {
  if (httpStatus !== 401 && httpStatus !== 403) {
    return null;
  }
  try {
    const data = JSON.parse(responseText) as ValidateResponseBody;
    if (data.valid === false) {
      return {
        ok: false,
        code: 'INVALID_KEY',
        message: typeof data.error === 'string' ? data.error : undefined,
        httpStatus,
      };
    }
  } catch {
    // not JSON
  }
  return null;
}

function parseValidationResult(
  data: ValidateResponseBody,
  serviceId: string | null,
): ServiceKeyValidationResult {
  const subscriptionStatus =
    typeof data.subscriptionStatus === 'string' ? data.subscriptionStatus : null;

  return {
    userId: data.userId ?? null,
    serviceId: data.serviceId ?? serviceId ?? null,
    serviceKeyId:
      typeof data.serviceKeyId === 'string' && data.serviceKeyId.length > 0 ? data.serviceKeyId : null,
    serviceProductId: typeof data.serviceProductId === 'string' ? data.serviceProductId : null,
    roles: Array.isArray(data.roles) ? data.roles : [],
    subscriptionStatus,
    validationSchemaVersion:
      typeof data.validationSchemaVersion === 'number' ? data.validationSchemaVersion : 0,
    billingModelType: data.billingModelType ?? null,
    measurementType: data.measurementType ?? null,
    unitLabel: data.unitLabel ?? null,
    grantAccess: grantAccess(subscriptionStatus),
  };
}

/**
 * Validates a service key via the Tollara API and verifies response HMAC.
 * Returns a discriminated outcome with canonical failure codes (§2.1.1).
 */
export async function validateServiceKeyWithOutcome(params: {
  baseUrl?: string | null;
  serviceKey: string;
  serviceId: string | null;
  serviceSecret: string;
  fetch?: typeof globalThis.fetch;
}): Promise<ServiceKeyValidationOutcome> {
  const { baseUrl, serviceKey, serviceId, serviceSecret, fetch: fetchFn = fetch } = params;
  if (!serviceKey?.trim()) {
    return { ok: false, code: 'MISSING_KEY' };
  }

  const origin = resolveBaseUrl(baseUrl, DEFAULT_API_URL);
  const url = `${joinUrl(origin, resolveCorePathPrefix(baseUrl))}/service-keys/validate`;
  const body = JSON.stringify({ serviceKey, serviceId, serviceSecret });

  let res: Response;
  try {
    res = await fetchFn(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body,
    });
  } catch {
    return { ok: false, code: 'NETWORK' };
  }

  const httpStatus = res.status;
  const responseText = await res.text();
  if (!res.ok) {
    const unsignedInvalid = invalidKeyFromUnsignedErrorBody(responseText, httpStatus);
    if (unsignedInvalid) {
      return unsignedInvalid;
    }
    return { ok: false, code: 'HTTP_ERROR', httpStatus };
  }

  const signature = res.headers.get(TollaraHeaders.SIGNATURE);
  const timestamp = res.headers.get(TollaraHeaders.TIMESTAMP);
  if (!signature || !timestamp) {
    return { ok: false, code: 'MISSING_SIGNATURE_HEADERS', httpStatus };
  }

  const dataToVerify = responseText + timestamp;
  const expectedSig = calculateHmac(dataToVerify, serviceSecret);
  if (!constantTimeEquals(expectedSig, signature)) {
    return { ok: false, code: 'HMAC_MISMATCH', httpStatus };
  }

  let data: ValidateResponseBody;
  try {
    data = JSON.parse(responseText) as ValidateResponseBody;
  } catch {
    return { ok: false, code: 'PARSE_ERROR', httpStatus };
  }

  if (!data.valid) {
    return {
      ok: false,
      code: 'INVALID_KEY',
      message: typeof data.error === 'string' ? data.error : undefined,
      httpStatus,
    };
  }

  return { ok: true, result: parseValidationResult(data, serviceId) };
}

/**
 * Validates a service key via the Tollara API and verifies response HMAC.
 */
export async function validateServiceKey(
  params: {
    baseUrl?: string | null;
    serviceKey: string;
    serviceId: string | null;
    serviceSecret: string;
    fetch?: typeof globalThis.fetch;
  }
): Promise<ServiceKeyValidationResult | null> {
  const outcome = await validateServiceKeyWithOutcome(params);
  return outcome.ok ? outcome.result : null;
}

function parseEstimateResult(data: Record<string, unknown>, httpStatus: number): UsageEstimateResult {
  return {
    sufficientCredits: Boolean(data.sufficientCredits),
    wouldExceedCap: Boolean(data.wouldExceedCap),
    wouldAllow: Boolean(data.wouldAllow),
    estimatedCost: typeof data.estimatedCost === 'number' ? data.estimatedCost : null,
    billingModelType: typeof data.billingModelType === 'string' ? data.billingModelType : null,
    measurementType: typeof data.measurementType === 'string' ? data.measurementType : null,
    unitLabel: typeof data.unitLabel === 'string' ? data.unitLabel : null,
    breakdown: parseUsageBreakdown(data.breakdown),
    estimateSchemaVersion: typeof data.estimateSchemaVersion === 'number' ? data.estimateSchemaVersion : 0,
    timestamp: typeof data.timestamp === 'number' ? data.timestamp : 0,
    httpStatus,
  };
}

/**
 * Usage pre-flight for a service key (Core). Same trust model as {@link validateServiceKey}.
 */
export async function estimateUsage(params: {
  baseUrl?: string | null;
  serviceKey: string;
  serviceId: string | null;
  serviceSecret: string;
  estimatedUnits: number;
  fetch?: typeof globalThis.fetch;
}): Promise<UsageEstimateResult | null> {
  const { baseUrl, serviceKey, serviceId, serviceSecret, estimatedUnits, fetch: fetchFn = fetch } = params;
  if (!serviceKey?.trim()) return null;
  if (estimatedUnits == null || !Number.isFinite(estimatedUnits) || estimatedUnits <= 0) return null;

  const origin = resolveBaseUrl(baseUrl, DEFAULT_API_URL);
  const url = `${joinUrl(origin, resolveCorePathPrefix(baseUrl))}/service-keys/estimate-usage`;
  const body = JSON.stringify({ serviceKey, serviceId, serviceSecret, estimatedUnits });

  let res: Response;
  try {
    res = await fetchFn(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body,
    });
  } catch {
    return null;
  }

  const code = res.status;
  if (code !== 200 && code !== 403 && code !== 429) return null;

  const responseText = await res.text();
  if (!responseText.trim()) return null;

  const signature = res.headers.get(TollaraHeaders.SIGNATURE);
  const timestamp = res.headers.get(TollaraHeaders.TIMESTAMP);
  if (!signature || !timestamp) return null;
  if (!validateHmacSignature(signature, responseText + timestamp, serviceSecret)) return null;

  let data: Record<string, unknown>;
  try {
    data = JSON.parse(responseText) as Record<string, unknown>;
  } catch {
    return null;
  }

  return parseEstimateResult(data, code);
}

/** Core JWT usage estimate (`POST …/billing/usage/estimate`). Not HMAC-signed (spec §2.2). */
export async function estimateUsageWithJwt(params: {
  baseUrl?: string | null;
  corePathPrefix?: string | null;
  bearerToken: string;
  userId: string;
  serviceId: string;
  estimatedUnits: number;
  fetch?: typeof globalThis.fetch;
}): Promise<UsageEstimateResult | null> {
  const {
    baseUrl,
    corePathPrefix,
    bearerToken,
    userId,
    serviceId,
    estimatedUnits,
    fetch: fetchFn = fetch,
  } = params;
  if (!bearerToken?.trim() || !userId?.trim() || !serviceId?.trim()) return null;
  if (estimatedUnits == null || !Number.isFinite(estimatedUnits) || estimatedUnits <= 0) return null;

  const origin = resolveBaseUrl(baseUrl, DEFAULT_API_URL);
  const prefix = resolveCorePathPrefix(baseUrl, corePathPrefix);
  const url = `${joinUrl(origin, prefix)}/billing/usage/estimate`;
  const body = JSON.stringify({ userId, serviceId, estimatedUnits });

  let res: Response;
  try {
    res = await fetchFn(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${bearerToken.trim()}`,
      },
      body,
    });
  } catch {
    return null;
  }

  const code = res.status;
  if (code !== 200 && code !== 403 && code !== 429) return null;
  const responseText = await res.text();
  if (!responseText.trim()) return null;

  let data: Record<string, unknown>;
  try {
    data = JSON.parse(responseText) as Record<string, unknown>;
  } catch {
    return null;
  }

  return parseEstimateResult(data, code);
}

const CACHE_TTL_MS = 60_000;

interface CachedResult {
  result: ServiceKeyValidationResult;
  ts: number;
}

/** Simple cache for validateServiceKey (optional). */
export function createValidationCache() {
  const cache = new Map<string, CachedResult>();
  return {
    get(serviceKey: string): ServiceKeyValidationResult | null {
      const entry = cache.get(serviceKey);
      if (!entry || Date.now() - entry.ts > CACHE_TTL_MS) return null;
      return entry.result;
    },
    set(serviceKey: string, result: ServiceKeyValidationResult) {
      cache.set(serviceKey, { result, ts: Date.now() });
    },
    clear() {
      cache.clear();
    },
  };
}
