/**
 * Mode A (caller): invoke an agent on the gateway.
 */

import type { PluginConfig, CallAgentParams } from './types';

export async function callAgent(
  config: Pick<PluginConfig, 'gatewayUrl' | 'serviceKey'>,
  params: CallAgentParams
): Promise<{ status: number; data: unknown }> {
  const { serviceId, endpointId, body, async: useAsync } = params;
  const gatewayUrl = (config.gatewayUrl || '').replace(/\/$/, '');
  const serviceKey = config.serviceKey;
  if (!gatewayUrl || !serviceKey) {
    throw new Error('AgentVend plugin: gatewayUrl and serviceKey required for caller mode');
  }
  const path = useAsync ? 'invoke/async' : 'invoke';
  const url = `${gatewayUrl}/api/service/${serviceId}/endpoint/${endpointId}/${path}`;
  const bodyStr = typeof body === 'string' ? body : body != null ? JSON.stringify(body) : '{}';
  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${serviceKey}`,
    },
    body: bodyStr,
  });
  const data = await res.json().catch(() => ({}));
  return { status: res.status, data };
}
