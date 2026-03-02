export interface PluginConfig {
  mode: 'caller' | 'backend';
  gatewayUrl?: string;
  agentKey?: string;
  coreServiceUrl?: string;
  usageServiceUrl?: string;
  agentSecret?: string;
}

export interface CallAgentParams {
  agentId: string;
  endpointId: string;
  body?: string | Record<string, unknown>;
  async?: boolean;
}
