/**
 * Mode A (caller): invoke an agent on the gateway.
 */

import type { PluginConfig, CallAgentParams } from './types';

export async function callAgent(
  config: Pick<PluginConfig, 'gatewayUrl' | 'agentKey'>,
  params: CallAgentParams
): Promise<{ status: number; data: unknown }> {
  const { agentId, endpointId, body, async: useAsync } = params;
  const gatewayUrl = (config.gatewayUrl || '').replace(/\/$/, '');
  const agentKey = config.agentKey;
  if (!gatewayUrl || !agentKey) {
    throw new Error('AgentVend plugin: gatewayUrl and agentKey required for caller mode');
  }
  const path = useAsync ? 'invoke/async' : 'invoke';
  const url = `${gatewayUrl}/api/agent/${agentId}/endpoint/${endpointId}/${path}`;
  const bodyStr = typeof body === 'string' ? body : body != null ? JSON.stringify(body) : '{}';
  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${agentKey}`,
    },
    body: bodyStr,
  });
  const data = await res.json().catch(() => ({}));
  return { status: res.status, data };
}
