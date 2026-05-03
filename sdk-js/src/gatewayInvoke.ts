import { DEFAULT_API_URL, DEFAULT_GATEWAY_PATH_PREFIX } from './constants';
import { resolveBaseUrl } from './urls';

function normalizePrefix(prefix: string): string {
  if (!prefix) return '';
  const p = prefix.startsWith('/') ? prefix : `/${prefix}`;
  return p.replace(/\/$/, '');
}

export type GatewayHttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE';

export type GatewayInvokeAsyncEnvelope = {
  requestId: string;
  callbackUrl: string;
  progressUrl: string;
};

export type GatewayInvokeResult = {
  statusCode: number;
  body: string;
  asyncEnvelope?: GatewayInvokeAsyncEnvelope;
};

/**
 * Gateway agent invoke (sync or async). See `docs-sdk/MAIN-SDK-API-SPEC.md` §1.1–1.2.
 */
export async function invokeAgent(params: {
  baseUrl?: string | null;
  gatewayPathPrefix?: string | null;
  method: GatewayHttpMethod;
  agentId: string;
  endpointId: string;
  agentKey: string;
  body?: string | null;
  async?: boolean;
  fetch?: typeof globalThis.fetch;
}): Promise<GatewayInvokeResult | null> {
  const {
    baseUrl,
    gatewayPathPrefix,
    method,
    agentId,
    endpointId,
    agentKey,
    body,
    async: isAsync,
    fetch: fetchFn = fetch,
  } = params;
  const origin = resolveBaseUrl(baseUrl, DEFAULT_API_URL);
  const prefix = normalizePrefix((gatewayPathPrefix ?? DEFAULT_GATEWAY_PATH_PREFIX).trim());
  const path = `${prefix}/agent/${agentId}/endpoint/${endpointId}/invoke${isAsync ? '/async' : ''}`;
  const url = `${origin}${path}`;
  const m = method.toUpperCase() as GatewayHttpMethod;
  const payload = body ?? '';
  const headers: Record<string, string> = { Authorization: `Bearer ${agentKey}` };
  const hasBody = payload.length > 0 && (m === 'POST' || m === 'PUT');
  if (hasBody) {
    headers['Content-Type'] = 'application/json';
  }
  try {
    const res = await fetchFn(url, {
      method: m,
      headers,
      body: m === 'GET' || m === 'DELETE' ? undefined : hasBody ? payload : undefined,
    });
    const text = await res.text();
    let asyncEnvelope: GatewayInvokeAsyncEnvelope | undefined;
    if (res.status === 202 && text.trim()) {
      try {
        const j = JSON.parse(text) as Record<string, unknown>;
        if (typeof j.requestId === 'string') {
          asyncEnvelope = {
            requestId: j.requestId,
            callbackUrl: typeof j.callbackUrl === 'string' ? j.callbackUrl : '',
            progressUrl: typeof j.progressUrl === 'string' ? j.progressUrl : '',
          };
        }
      } catch {
        /* ignore */
      }
    }
    return { statusCode: res.status, body: text, asyncEnvelope };
  } catch {
    return null;
  }
}
