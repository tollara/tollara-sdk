export interface PluginConfig {
  mode: 'caller' | 'backend';
  gatewayUrl?: string;
  serviceKey?: string;
  /** AgentVend API origin; defaults to production when unset (usage reporting in backend mode). */
  apiUrl?: string;
  serviceSecret?: string;
}

export interface CallAgentParams {
  serviceId: string;
  endpointId: string;
  body?: string | Record<string, unknown>;
  async?: boolean;
}
