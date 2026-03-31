/**
 * Caller-side gateway polling for async jobs (docs/sdk-api-spec.md §1.3–1.4).
 */

function normalizeGatewayBase(url: string): string {
  return url.replace(/\/$/, '');
}

function normalizePrefix(prefix: string): string {
  if (!prefix) return '';
  const p = prefix.startsWith('/') ? prefix : `/${prefix}`;
  return p.replace(/\/$/, '');
}

function buildUrl(gatewayBaseUrl: string, gatewayPathPrefix: string, suffix: string): string {
  return `${normalizeGatewayBase(gatewayBaseUrl)}${normalizePrefix(gatewayPathPrefix)}${suffix}`;
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
  gatewayBaseUrl: string;
  gatewayPathPrefix: string;
  requestId: string;
  agentKey: string;
  fetch?: typeof globalThis.fetch;
}): Promise<GatewayPollResult> {
  const { gatewayBaseUrl, gatewayPathPrefix, requestId, agentKey, fetch: fetchFn = fetch } = params;
  const url = buildUrl(gatewayBaseUrl, gatewayPathPrefix, `/requests/${requestId}/status`);
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
  gatewayBaseUrl: string;
  gatewayPathPrefix: string;
  requestId: string;
  agentKey: string;
  fetch?: typeof globalThis.fetch;
}): Promise<GatewayPollResult> {
  const { gatewayBaseUrl, gatewayPathPrefix, requestId, agentKey, fetch: fetchFn = fetch } = params;
  const url = buildUrl(gatewayBaseUrl, gatewayPathPrefix, `/requests/${requestId}/result`);
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
