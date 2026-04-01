export interface PluginConfig {
  mode: 'caller' | 'backend';
  gatewayUrl?: string;
  agentKey?: string;
  /** AgentVend API origin; defaults to production when unset (usage reporting in backend mode). */
  apiUrl?: string;
  agentSecret?: string;
}

export interface CallAgentParams {
  agentId: string;
  endpointId: string;
  body?: string | Record<string, unknown>;
  async?: boolean;
}
