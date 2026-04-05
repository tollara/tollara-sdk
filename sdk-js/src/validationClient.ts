import { AgentVendHeaders } from './agentVendHeaders';
import { DEFAULT_API_URL, DEFAULT_CORE_PATH_PREFIX } from './constants';
import { calculateHmac, constantTimeEquals, validateHmacSignature } from './hmac';
import { joinUrl, resolveBaseUrl } from './urls';

export interface AgentKeyValidationResult {
  userId: string | null;
  agentId: string | null;
  plan: string | null;
  roles: string[];
  quotaRemaining: number | null;
  subscriptionActive: boolean;
  billingModelType: string | null;
  measurementType: string | null;
  unitLabel: string | null;
}

export interface UsageEstimateResult {
  sufficientCredits: boolean;
  wouldExceedCap: boolean;
  wouldAllow: boolean;
  estimatedCost: number | null;
  remainingCredits: number | null;
  remainingSpendingCap: number | null;
  billingModelType: string | null;
  measurementType: string | null;
  unitLabel: string | null;
  breakdown: Record<string, unknown> | null;
  estimateSchemaVersion: number;
  timestamp: number;
  httpStatus: number;
}

const CACHE_TTL_MS = 60_000;

interface CachedResult {
  result: AgentKeyValidationResult;
  ts: number;
}

/**
 * Validates an agent key via the AgentVend API and verifies response HMAC.
 * Uses `baseUrl` (default production API origin) + `/api/v1/agent-keys/validate`.
 * Optional in-memory cache with 60s TTL via {@link createValidationCache}.
 */
export async function validateAgentKey(
  params: {
    /** API origin; defaults to `https://api.agentvend.api`. */
    baseUrl?: string | null;
    agentKey: string;
    agentId: string | null;
    agentSecret: string;
    fetch?: typeof globalThis.fetch;
  }
): Promise<AgentKeyValidationResult | null> {
  const { baseUrl, agentKey, agentId, agentSecret, fetch: fetchFn = fetch } = params;
  if (!agentKey?.trim()) return null;

  const origin = resolveBaseUrl(baseUrl, DEFAULT_API_URL);
  const url = `${joinUrl(origin, DEFAULT_CORE_PATH_PREFIX)}/agent-keys/validate`;
  const body = JSON.stringify({ agentKey, agentId, agentSecret });

  let res: Response;
  try {
    res = await fetchFn(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body,
    });
  } catch (e) {
    return null;
  }

  if (!res.ok) return null;

  const responseText = await res.text();
  const signature = res.headers.get(AgentVendHeaders.SIGNATURE);
  const timestamp = res.headers.get(AgentVendHeaders.TIMESTAMP);
  if (!signature || !timestamp) return null;

  const dataToVerify = responseText + timestamp;
  const expectedSig = calculateHmac(dataToVerify, agentSecret);
  if (!constantTimeEquals(expectedSig, signature)) return null;

  let data: {
    valid?: boolean;
    userId?: string;
    agentId?: string;
    plan?: string;
    roles?: string[];
    quotaRemaining?: number;
    subscriptionActive?: boolean;
    billingModelType?: string | null;
    measurementType?: string | null;
    unitLabel?: string | null;
    error?: string;
  };
  try {
    data = JSON.parse(responseText);
  } catch {
    return null;
  }

  if (!data.valid) return null;

  return {
    userId: data.userId ?? null,
    agentId: data.agentId ?? agentId ?? null,
    plan: data.plan ?? null,
    roles: Array.isArray(data.roles) ? data.roles : [],
    quotaRemaining: typeof data.quotaRemaining === 'number' ? data.quotaRemaining : null,
    subscriptionActive: Boolean(data.subscriptionActive),
    billingModelType: data.billingModelType ?? null,
    measurementType: data.measurementType ?? null,
    unitLabel: data.unitLabel ?? null,
  };
}

/**
 * Usage pre-flight for an agent key (Core). Same trust model as {@link validateAgentKey}: JSON body, response HMAC.
 * Verifies signatures on 200 / 403 / 429 when `X-AgentVend-Signature` and `X-AgentVend-Timestamp` are present.
 */
export async function estimateUsage(params: {
  baseUrl?: string | null;
  agentKey: string;
  agentId: string | null;
  agentSecret: string;
  estimatedUnits: number;
  fetch?: typeof globalThis.fetch;
}): Promise<UsageEstimateResult | null> {
  const { baseUrl, agentKey, agentId, agentSecret, estimatedUnits, fetch: fetchFn = fetch } = params;
  if (!agentKey?.trim()) return null;
  if (estimatedUnits == null || !Number.isFinite(estimatedUnits) || estimatedUnits <= 0) return null;

  const origin = resolveBaseUrl(baseUrl, DEFAULT_API_URL);
  const url = `${joinUrl(origin, DEFAULT_CORE_PATH_PREFIX)}/agent-keys/estimate-usage`;
  const body = JSON.stringify({ agentKey, agentId, agentSecret, estimatedUnits });

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

  const signature = res.headers.get(AgentVendHeaders.SIGNATURE);
  const timestamp = res.headers.get(AgentVendHeaders.TIMESTAMP);
  if (!signature || !timestamp) return null;
  if (!validateHmacSignature(signature, responseText + timestamp, agentSecret)) return null;

  let data: Record<string, unknown>;
  try {
    data = JSON.parse(responseText) as Record<string, unknown>;
  } catch {
    return null;
  }

  const br = data.breakdown;
  const breakdown =
    br != null && typeof br === 'object' && !Array.isArray(br) ? (br as Record<string, unknown>) : null;

  return {
    sufficientCredits: Boolean(data.sufficientCredits),
    wouldExceedCap: Boolean(data.wouldExceedCap),
    wouldAllow: Boolean(data.wouldAllow),
    estimatedCost: typeof data.estimatedCost === 'number' ? data.estimatedCost : null,
    remainingCredits: typeof data.remainingCredits === 'number' ? data.remainingCredits : null,
    remainingSpendingCap: typeof data.remainingSpendingCap === 'number' ? data.remainingSpendingCap : null,
    billingModelType: typeof data.billingModelType === 'string' ? data.billingModelType : null,
    measurementType: typeof data.measurementType === 'string' ? data.measurementType : null,
    unitLabel: typeof data.unitLabel === 'string' ? data.unitLabel : null,
    breakdown,
    estimateSchemaVersion: typeof data.estimateSchemaVersion === 'number' ? data.estimateSchemaVersion : 0,
    timestamp: typeof data.timestamp === 'number' ? data.timestamp : 0,
    httpStatus: code,
  };
}

/**
 * Simple cache for validateAgentKey (optional).
 */
export function createValidationCache() {
  const cache = new Map<string, CachedResult>();
  return {
    get(agentKey: string): AgentKeyValidationResult | null {
      const entry = cache.get(agentKey);
      if (!entry || Date.now() - entry.ts > CACHE_TTL_MS) return null;
      return entry.result;
    },
    set(agentKey: string, result: AgentKeyValidationResult) {
      cache.set(agentKey, { result, ts: Date.now() });
    },
    clear() {
      cache.clear();
    },
  };
}
