/**
 * Mode B (backend): verify HMAC and report usage.
 * Use with your HTTP server: call verifyRequest, then your logic, then reportUsageIfNeeded.
 */

import { verifySignature, getUserContext, reportUsage } from '@agentvend/agent-sdk';
import type { PluginConfig } from './types';

export interface IncomingRequest {
  body: string | unknown;
  headers: Record<string, string | string[] | undefined>;
}

export interface BackendResult {
  verified: boolean;
  userContext?: ReturnType<typeof getUserContext>;
  error?: string;
}

export function verifyRequest(
  config: Pick<PluginConfig, 'agentSecret'>,
  req: IncomingRequest
): BackendResult {
  const secret = config.agentSecret;
  if (!secret) {
    return { verified: false, error: 'agentSecret not configured' };
  }
  const get = (name: string) => {
    const v = req.headers[name.toLowerCase()] ?? req.headers[name];
    return Array.isArray(v) ? v[0] : v;
  };
  const signature = get('x-agentvend-signature') ?? '';
  const timestamp = get('x-agentvend-timestamp') ?? '';
  const payload =
    typeof req.body === 'string' ? req.body : req.body != null ? JSON.stringify(req.body) : '';
  const valid = verifySignature(secret, {
    signature,
    timestamp,
    payload,
    userId: get('x-agentvend-user-id') ?? null,
    plan: get('x-agentvend-plan') ?? null,
    roles: (get('x-agentvend-roles') ?? '').split(',').filter(Boolean),
    quotaRemaining: get('x-agentvend-quota-remaining') ?? null,
  });
  if (!valid) {
    return { verified: false, error: 'Invalid HMAC signature' };
  }
  const userContext = getUserContext({
    'X-AgentVend-User-ID': get('x-agentvend-user-id'),
    'X-AgentVend-Plan': get('x-agentvend-plan'),
    'X-AgentVend-Roles': get('x-agentvend-roles'),
    'X-AgentVend-Quota-Remaining': get('x-agentvend-quota-remaining'),
    'X-AgentVend-Subscription-Active': get('x-agentvend-subscription-active'),
  });
  return { verified: true, userContext };
}

export async function reportUsageIfNeeded(
  config: Pick<PluginConfig, 'usageServiceUrl' | 'agentSecret'>,
  params: { userId: string; agentId: string; unitsUsed: number }
): Promise<void> {
  const { usageServiceUrl, agentSecret } = config;
  if (!usageServiceUrl || !agentSecret) return;
  await reportUsage({
    usageServiceUrl,
    userId: params.userId,
    agentId: params.agentId,
    unitsUsed: params.unitsUsed,
    agentSecret,
  });
}
