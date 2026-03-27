import { calculateHmac, constantTimeEquals } from './hmac';

export interface AgentKeyValidationResult {
  userId: string | null;
  agentId: string | null;
  plan: string | null;
  roles: string[];
  quotaRemaining: number | null;
  subscriptionActive: boolean;
}

const CACHE_TTL_MS = 60_000;

interface CachedResult {
  result: AgentKeyValidationResult;
  ts: number;
}

/**
 * Validates an agent key via the core service and verifies response HMAC.
 * Optional in-memory cache with 60s TTL.
 */
export async function validateAgentKey(
  params: {
    coreServiceUrl: string;
    agentKey: string;
    agentId: string | null;
    agentSecret: string;
    fetch?: typeof globalThis.fetch;
  }
): Promise<AgentKeyValidationResult | null> {
  const { coreServiceUrl, agentKey, agentId, agentSecret, fetch: fetchFn = fetch } = params;
  if (!agentKey?.trim()) return null;

  const url = `${coreServiceUrl.replace(/\/$/, '')}/agent-keys/validate`;
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
  const signature = res.headers.get('X-AgentVend-Signature');
  const timestamp = res.headers.get('X-AgentVend-Timestamp');
  if (!signature || !timestamp) return null;

  const dataToVerify = responseText + timestamp;
  const expectedSig = calculateHmac(dataToVerify, agentSecret);
  if (!constantTimeEquals(expectedSig, signature)) return null;

  let data: { valid?: boolean; userId?: string; agentId?: string; plan?: string; roles?: string[]; quotaRemaining?: number; subscriptionActive?: boolean; error?: string };
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
