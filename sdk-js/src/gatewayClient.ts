/**
 * Caller-side gateway polling for async jobs.
 */

import { DEFAULT_API_URL, DEFAULT_GATEWAY_PATH_PREFIX } from './constants';
import { resolveBaseUrl } from './urls';

function normalizePrefix(prefix: string): string {
  if (!prefix) return '';
  const p = prefix.startsWith('/') ? prefix : `/${prefix}`;
  return p.replace(/\/$/, '');
}

function buildUrl(origin: string, gatewayPathPrefix: string, suffix: string): string {
  const base = resolveBaseUrl(origin, DEFAULT_API_URL);
  return `${base}${normalizePrefix(gatewayPathPrefix)}${suffix}`;
}

export interface GatewayPollResult {
  ok: boolean;
  status: number;
  body: string;
}

/**
 * GET .../requests/{requestId}/status with Bearer agent key.
 */
export async function getRequestStatus(params: {
  /** API origin; defaults to `https://api.agentvend.api`. */
  baseUrl?: string | null;
  requestId: string;
  agentKey: string;
  fetch?: typeof globalThis.fetch;
}): Promise<GatewayPollResult> {
  const { baseUrl, requestId, agentKey, fetch: fetchFn = fetch } = params;
  const url = buildUrl(baseUrl ?? DEFAULT_API_URL, DEFAULT_GATEWAY_PATH_PREFIX, `/requests/${requestId}/status`);
  try {
    const res = await fetchFn(url, {
      method: 'GET',
      headers: { Authorization: `Bearer ${agentKey}` },
    });
    const body = await res.text();
    return { ok: res.ok, status: res.status, body };
  } catch {
    return { ok: false, status: 0, body: '' };
  }
}

/**
 * GET .../requests/{requestId}/result with Bearer agent key.
 */
export async function getRequestResult(params: {
  /** API origin; defaults to `https://api.agentvend.api`. */
  baseUrl?: string | null;
  requestId: string;
  agentKey: string;
  fetch?: typeof globalThis.fetch;
}): Promise<GatewayPollResult> {
  const { baseUrl, requestId, agentKey, fetch: fetchFn = fetch } = params;
  const url = buildUrl(baseUrl ?? DEFAULT_API_URL, DEFAULT_GATEWAY_PATH_PREFIX, `/requests/${requestId}/result`);
  try {
    const res = await fetchFn(url, {
      method: 'GET',
      headers: { Authorization: `Bearer ${agentKey}` },
    });
    const body = await res.text();
    return { ok: res.ok, status: res.status, body };
  } catch {
    return { ok: false, status: 0, body: '' };
  }
}
