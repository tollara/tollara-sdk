import { CompletionStatus } from './completionStatus';
import { getRequestResult, getRequestStatus, type GatewayPollResult } from './gatewayClient';
import {
  DEFAULT_USAGE_PATH_PREFIX,
  reportCompletion,
  reportCompletionFull,
  reportProgress,
  reportUsage,
  type UsageReportResponse,
} from './usageClient';
import { validateAgentKey, type AgentKeyValidationResult } from './validationClient';

export const ENV_API_URL = 'AGENTVEND_API_URL';
export const ENV_AGENT_ID = 'AGENTVEND_AGENT_ID';
export const ENV_AGENT_SECRET = 'AGENTVEND_AGENT_SECRET';

/** Production API origin; used when neither `apiUrl` nor `AGENTVEND_API_URL` is set. */
export const DEFAULT_API_URL = 'https://api.agentvend.api';

export const DEFAULT_CORE_PATH_PREFIX = '/api/v1';
export const DEFAULT_GATEWAY_PATH_PREFIX = '/api';

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

function trimTrailingSlashes(s: string): string {
  let t = s.trim();
  while (t.endsWith('/')) t = t.slice(0, -1);
  return t;
}

function joinUrl(base: string, path: string | null | undefined): string {
  const b = trimTrailingSlashes(base);
  if (path == null || path === '') return b;
  const p = path.startsWith('/') ? path : `/${path}`;
  return b + p;
}

export type AgentVendClientOptions = {
  apiUrl?: string | null;
  coreApiUrl?: string | null;
  gatewayApiUrl?: string | null;
  usageApiUrl?: string | null;
  corePathPrefix?: string | null;
  gatewayPathPrefix?: string | null;
  usagePathPrefix?: string | null;
  agentId?: string | null;
  agentSecret?: string | null;
  fetch?: typeof globalThis.fetch;
};

/**
 * Unified client: Core validate, Usage report/progress/complete, Gateway polling.
 * Omitted options fall back to `AGENTVEND_*` environment variables (when `process.env` exists).
 * The API origin defaults to `DEFAULT_API_URL` when neither `apiUrl` nor `AGENTVEND_API_URL` is set.
 */
export class AgentVendClient {
  static readonly ENV_API_URL = ENV_API_URL;
  static readonly ENV_AGENT_ID = ENV_AGENT_ID;
  static readonly ENV_AGENT_SECRET = ENV_AGENT_SECRET;
  static readonly DEFAULT_API_URL = DEFAULT_API_URL;
  static readonly DEFAULT_CORE_PATH_PREFIX = DEFAULT_CORE_PATH_PREFIX;
  static readonly DEFAULT_GATEWAY_PATH_PREFIX = DEFAULT_GATEWAY_PATH_PREFIX;
  static readonly DEFAULT_USAGE_PATH_PREFIX = DEFAULT_USAGE_PATH_PREFIX;

  private readonly gatewayBaseUrl: string;
  private readonly gatewayPathPrefix: string;
  private readonly coreRoot: string;
  private readonly usageBase: string;
  private readonly usagePathPrefix: string;
  private readonly agentId: string | null;
  private readonly agentSecret: string;
  private readonly fetchFn: typeof globalThis.fetch;

  constructor(options: AgentVendClientOptions = {}) {
    let resolved = trimTrailingSlashes(firstNonBlank(options.apiUrl, envGet(ENV_API_URL)));
    if (!resolved) {
      resolved = DEFAULT_API_URL;
    }

    const coreBase = trimTrailingSlashes(firstNonBlank(options.coreApiUrl, resolved));
    const gwBase = trimTrailingSlashes(firstNonBlank(options.gatewayApiUrl, resolved));
    const usageBase = trimTrailingSlashes(firstNonBlank(options.usageApiUrl, resolved));

    const corePrefix = options.corePathPrefix != null ? options.corePathPrefix : DEFAULT_CORE_PATH_PREFIX;
    const gwPrefix = options.gatewayPathPrefix != null ? options.gatewayPathPrefix : DEFAULT_GATEWAY_PATH_PREFIX;
    const usagePrefix =
      options.usagePathPrefix != null ? options.usagePathPrefix : DEFAULT_USAGE_PATH_PREFIX;

    const secret = firstNonBlank(options.agentSecret, envGet(ENV_AGENT_SECRET));
    if (!secret) {
      throw new Error(
        `Agent secret is required: set agentSecret or environment variable ${ENV_AGENT_SECRET}`
      );
    }

    const aidRaw = firstNonBlank(options.agentId, envGet(ENV_AGENT_ID));
    const aid = aidRaw === '' ? null : aidRaw || null;

    this.gatewayBaseUrl = gwBase;
    this.gatewayPathPrefix = gwPrefix;
    this.coreRoot = joinUrl(coreBase, corePrefix);
    this.usageBase = usageBase;
    this.usagePathPrefix = usagePrefix;
    this.agentId = aid;
    this.agentSecret = secret;
    this.fetchFn = options.fetch ?? fetch;
  }

  async validateAgentKey(agentKey: string): Promise<AgentKeyValidationResult | null> {
    return validateAgentKey({
      coreServiceUrl: this.coreRoot,
      agentKey,
      agentId: this.agentId,
      agentSecret: this.agentSecret,
      fetch: this.fetchFn,
    });
  }

  async reportUsage(userId: string, agentId: string, unitsUsed: number): Promise<UsageReportResponse> {
    return reportUsage({
      usageServiceUrl: this.usageBase,
      userId,
      agentId,
      unitsUsed,
      agentSecret: this.agentSecret,
      usagePathPrefix: this.usagePathPrefix,
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
      gatewayBaseUrl: this.gatewayBaseUrl,
      gatewayPathPrefix: this.gatewayPathPrefix,
      requestId,
      agentKey,
      fetch: this.fetchFn,
    });
  }

  async getRequestResult(requestId: string, agentKey: string): Promise<GatewayPollResult> {
    return getRequestResult({
      gatewayBaseUrl: this.gatewayBaseUrl,
      gatewayPathPrefix: this.gatewayPathPrefix,
      requestId,
      agentKey,
      fetch: this.fetchFn,
    });
  }
}
