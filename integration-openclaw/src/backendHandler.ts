/**
 * Mode B (backend): verify HMAC and report usage.
 * Use with your HTTP server: call verifyRequest, then your logic, then reportUsageIfNeeded.
 */

import { verifySignatureFromHeaders, getUserContext, reportUsage } from '@agentvend/agent-sdk';
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
  const payload =
    typeof req.body === 'string' ? req.body : req.body != null ? JSON.stringify(req.body) : '';
  const headerBag: Record<string, string | string[] | undefined> = { ...req.headers };
  const valid = verifySignatureFromHeaders(secret, headerBag, payload);
  if (!valid) {
    return { verified: false, error: 'Invalid HMAC signature' };
  }
  const userContext = getUserContext(headerBag);
  return { verified: true, userContext };
}

export async function reportUsageIfNeeded(
  config: Pick<PluginConfig, 'apiUrl' | 'agentSecret'>,
  params: { userId: string; agentId: string; unitsUsed: number }
): Promise<void> {
  const { apiUrl, agentSecret } = config;
  if (!agentSecret) return;
  await reportUsage({
    ...(apiUrl ? { baseUrl: apiUrl } : {}),
    userId: params.userId,
    agentId: params.agentId,
    unitsUsed: params.unitsUsed,
    agentSecret,
  });
}
