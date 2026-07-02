/**
 * Caller-side gateway polling for async jobs.
 */

import { DEFAULT_API_URL } from './constants';
import { resolveGatewayPathPrefix } from './pathPrefixes';
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
 * GET .../requests/{requestId}/status with Bearer service key.
 */
export async function getRequestStatus(params: {
  /** API origin; defaults to `https://api.tollara.ai`. */
  baseUrl?: string | null;
  requestId: string;
  serviceKey: string;
  fetch?: typeof globalThis.fetch;
}): Promise<GatewayPollResult> {
  const { baseUrl, requestId, serviceKey, fetch: fetchFn = fetch } = params;
  const url = buildUrl(
    baseUrl ?? DEFAULT_API_URL,
    resolveGatewayPathPrefix(baseUrl),
    `/requests/${requestId}/status`,
  );
  try {
    const res = await fetchFn(url, {
      method: 'GET',
      headers: { Authorization: `Bearer ${serviceKey}` },
    });
    const body = await res.text();
    return { ok: res.ok, status: res.status, body };
  } catch {
    return { ok: false, status: 0, body: '' };
  }
}

/**
 * GET .../requests/{requestId}/result with Bearer service key.
 */
export async function getRequestResult(params: {
  /** API origin; defaults to `https://api.tollara.ai`. */
  baseUrl?: string | null;
  requestId: string;
  serviceKey: string;
  fetch?: typeof globalThis.fetch;
}): Promise<GatewayPollResult> {
  const { baseUrl, requestId, serviceKey, fetch: fetchFn = fetch } = params;
  const url = buildUrl(
    baseUrl ?? DEFAULT_API_URL,
    resolveGatewayPathPrefix(baseUrl),
    `/requests/${requestId}/result`,
  );
  try {
    const res = await fetchFn(url, {
      method: 'GET',
      headers: { Authorization: `Bearer ${serviceKey}` },
    });
    const body = await res.text();
    return { ok: res.ok, status: res.status, body };
  } catch {
    return { ok: false, status: 0, body: '' };
  }
}
