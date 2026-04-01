import { CompletionStatus } from './completionStatus';
import {
  DEFAULT_API_URL,
  DEFAULT_CORE_PATH_PREFIX,
  DEFAULT_GATEWAY_PATH_PREFIX,
  DEFAULT_USAGE_PATH_PREFIX,
} from './constants';
import { getRequestResult, getRequestStatus, type GatewayPollResult } from './gatewayClient';
import {
  reportCompletion,
  reportCompletionFull,
  reportProgress,
  reportUsage,
  type UsageReportResponse,
} from './usageClient';
import { validateAgentKey, type AgentKeyValidationResult } from './validationClient';
import { resolveBaseUrl } from './urls';

export const ENV_API_URL = 'AGENTVEND_API_URL';
export const ENV_AGENT_ID = 'AGENTVEND_AGENT_ID';
export const ENV_AGENT_SECRET = 'AGENTVEND_AGENT_SECRET';

/** Re-exported for callers that read the default origin. */
export { DEFAULT_API_URL, DEFAULT_CORE_PATH_PREFIX, DEFAULT_GATEWAY_PATH_PREFIX, DEFAULT_USAGE_PATH_PREFIX };

function envGet(name: string): string | undefined {
  if (typeof process !== 'undefined' && process.env && typeof process.env[name] === 'string') {
    return process.env[name];
  }
  return undefined;
}

function firstNonBlank(a: string | null | undefined, b: string | null | undefined): string {
  const t = (a ?? '').trim();
  if (t) return t;
  return (b ?? '').trim();
}

export type AgentVendClientOptions = {
  /**
   * API origin (scheme + host). Defaults to `https://api.agentvend.api` or `AGENTVEND_API_URL`.
   * All service calls use this origin with fixed paths (validate, usage, gateway polling).
   */
  apiUrl?: string | null;
  agentId?: string | null;
  agentSecret?: string | null;
  fetch?: typeof globalThis.fetch;
};

/**
 * Unified client: validate agent key, report usage/progress/complete, poll gateway for async jobs.
 * Omitted options fall back to `AGENTVEND_*` environment variables (when `process.env` exists).
 */
export class AgentVendClient {
  static readonly ENV_API_URL = ENV_API_URL;
  static readonly ENV_AGENT_ID = ENV_AGENT_ID;
  static readonly ENV_AGENT_SECRET = ENV_AGENT_SECRET;
  static readonly DEFAULT_API_URL = DEFAULT_API_URL;
  static readonly DEFAULT_CORE_PATH_PREFIX = DEFAULT_CORE_PATH_PREFIX;
  static readonly DEFAULT_GATEWAY_PATH_PREFIX = DEFAULT_GATEWAY_PATH_PREFIX;
  static readonly DEFAULT_USAGE_PATH_PREFIX = DEFAULT_USAGE_PATH_PREFIX;

  private readonly apiOrigin: string;
  private readonly agentId: string | null;
  private readonly agentSecret: string;
  private readonly fetchFn: typeof globalThis.fetch;

  constructor(options: AgentVendClientOptions = {}) {
    const resolved = resolveBaseUrl(firstNonBlank(options.apiUrl, envGet(ENV_API_URL)), DEFAULT_API_URL);

    const secret = firstNonBlank(options.agentSecret, envGet(ENV_AGENT_SECRET));
    if (!secret) {
      throw new Error(
        `Agent secret is required: set agentSecret or environment variable ${ENV_AGENT_SECRET}`
      );
    }

    const aidRaw = firstNonBlank(options.agentId, envGet(ENV_AGENT_ID));
    const aid = aidRaw === '' ? null : aidRaw || null;

    this.apiOrigin = resolved;
    this.agentId = aid;
    this.agentSecret = secret;
    this.fetchFn = options.fetch ?? fetch;
  }

  async validateAgentKey(agentKey: string): Promise<AgentKeyValidationResult | null> {
    return validateAgentKey({
      baseUrl: this.apiOrigin,
      agentKey,
      agentId: this.agentId,
      agentSecret: this.agentSecret,
      fetch: this.fetchFn,
    });
  }

  async reportUsage(userId: string, agentId: string, unitsUsed: number): Promise<UsageReportResponse> {
    return reportUsage({
      baseUrl: this.apiOrigin,
      userId,
      agentId,
      unitsUsed,
      agentSecret: this.agentSecret,
      fetch: this.fetchFn,
    });
  }

  async sendProgressUpdate(
    progressUrl: string,
    requestId: string,
    stage: string,
    percentageComplete: number,
    errorMessage?: string | null
  ): Promise<boolean> {
    return reportProgress({
      progressUrl,
      requestId,
      stage,
      percentageComplete,
      errorMessage,
      agentSecret: this.agentSecret,
      fetch: this.fetchFn,
    });
  }

  async sendCompletion(
    callbackUrl: string,
    requestId: string,
    status: CompletionStatus,
    units: number,
    options?: { result?: string | null; resultUrl?: string | null; contentType?: string | null }
  ): Promise<boolean> {
    const { result, resultUrl, contentType } = options ?? {};
    if (result != null || resultUrl != null || contentType != null) {
      return reportCompletionFull({
        callbackUrl,
        requestId,
        status,
        result,
        resultUrl,
        contentType,
        units,
        agentSecret: this.agentSecret,
        fetch: this.fetchFn,
      });
    }
    return reportCompletion({
      callbackUrl,
      requestId,
      status,
      units,
      agentSecret: this.agentSecret,
      fetch: this.fetchFn,
    });
  }

  async getRequestStatus(requestId: string, agentKey: string): Promise<GatewayPollResult> {
    return getRequestStatus({
      baseUrl: this.apiOrigin,
      requestId,
      agentKey,
      fetch: this.fetchFn,
    });
  }

  async getRequestResult(requestId: string, agentKey: string): Promise<GatewayPollResult> {
    return getRequestResult({
      baseUrl: this.apiOrigin,
      requestId,
      agentKey,
      fetch: this.fetchFn,
    });
  }
}
