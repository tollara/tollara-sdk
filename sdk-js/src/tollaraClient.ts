import { CompletionStatus } from './completionStatus';
import {
  DEFAULT_API_URL,
  DEFAULT_CORE_PATH_PREFIX,
  DEFAULT_GATEWAY_PATH_PREFIX,
  DEFAULT_USAGE_PATH_PREFIX,
} from './constants';
import { getRequestResult, getRequestStatus, type GatewayPollResult } from './gatewayClient';
import { invokeService, type GatewayHttpMethod, type GatewayInvokeResult } from './gatewayInvoke';
import {
  reportCompletion,
  reportCompletionFull,
  reportProgress,
  reportUsage,
  type UsageReportResponse,
} from './usageClient';
import {
  estimateUsage,
  estimateUsageWithJwt,
  validateServiceKey,
  type ServiceKeyValidationResult,
  type UsageEstimateResult,
} from './validationClient';
import { resolveBaseUrl } from './urls';

export const ENV_API_URL = 'TOLLARA_API_URL';
export const ENV_SERVICE_ID = 'TOLLARA_SERVICE_ID';
export const ENV_SERVICE_SECRET = 'TOLLARA_SERVICE_SECRET';
export const ENV_AGENT_ID = 'TOLLARA_AGENT_ID';
export const ENV_AGENT_SECRET = 'TOLLARA_AGENT_SECRET';

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

export type TollaraClientOptions = {
  /**
   * API origin (scheme + host). Defaults to `https://api.tollara.ai` or `TOLLARA_API_URL`.
   * All service calls use this origin with fixed paths (validate, usage, gateway polling).
   */
  apiUrl?: string | null;
  /** Service UUID; falls back to `TOLLARA_SERVICE_ID` (legacy `TOLLARA_AGENT_ID` also accepted). */
  serviceId?: string | null;
  /** Shared secret; falls back to `TOLLARA_SERVICE_SECRET` (legacy `TOLLARA_AGENT_SECRET` also accepted). */
  serviceSecret?: string | null;
  fetch?: typeof globalThis.fetch;
};

/**
 * Unified client: validate service key, report usage/progress/complete, poll gateway for async jobs.
 * Omitted options fall back to `TOLLARA_*` environment variables (when `process.env` exists).
 */
export class TollaraClient {
  static readonly ENV_API_URL = ENV_API_URL;
  static readonly ENV_SERVICE_ID = ENV_SERVICE_ID;
  static readonly ENV_SERVICE_SECRET = ENV_SERVICE_SECRET;
  static readonly ENV_AGENT_ID = ENV_AGENT_ID;
  static readonly ENV_AGENT_SECRET = ENV_AGENT_SECRET;
  static readonly DEFAULT_API_URL = DEFAULT_API_URL;
  static readonly DEFAULT_CORE_PATH_PREFIX = DEFAULT_CORE_PATH_PREFIX;
  static readonly DEFAULT_GATEWAY_PATH_PREFIX = DEFAULT_GATEWAY_PATH_PREFIX;
  static readonly DEFAULT_USAGE_PATH_PREFIX = DEFAULT_USAGE_PATH_PREFIX;

  private readonly apiOrigin: string;
  private readonly serviceId: string | null;
  private readonly serviceSecret: string;
  private readonly fetchFn: typeof globalThis.fetch;

  constructor(options: TollaraClientOptions = {}) {
    const resolved = resolveBaseUrl(firstNonBlank(options.apiUrl, envGet(ENV_API_URL)), DEFAULT_API_URL);

    const secret = firstNonBlank(options.serviceSecret, firstNonBlank(envGet(ENV_SERVICE_SECRET), envGet(ENV_AGENT_SECRET)));
    if (!secret) {
      throw new Error(
        `Service secret is required: set serviceSecret or environment variable ${ENV_SERVICE_SECRET}`
      );
    }

    const sidRaw = firstNonBlank(options.serviceId, firstNonBlank(envGet(ENV_SERVICE_ID), envGet(ENV_AGENT_ID)));
    const sid = sidRaw === '' ? null : sidRaw || null;

    this.apiOrigin = resolved;
    this.serviceId = sid;
    this.serviceSecret = secret;
    this.fetchFn = options.fetch ?? fetch;
  }

  async validateServiceKey(serviceKey: string): Promise<ServiceKeyValidationResult | null> {
    return validateServiceKey({
      baseUrl: this.apiOrigin,
      serviceKey,
      serviceId: this.serviceId,
      serviceSecret: this.serviceSecret,
      fetch: this.fetchFn,
    });
  }

  async estimateUsage(serviceKey: string, estimatedUnits: number): Promise<UsageEstimateResult | null> {
    return estimateUsage({
      baseUrl: this.apiOrigin,
      serviceKey,
      serviceId: this.serviceId,
      serviceSecret: this.serviceSecret,
      estimatedUnits,
      fetch: this.fetchFn,
    });
  }

  /**
   * Core JWT usage estimate (`POST …/billing/usage/estimate`). Response is not HMAC-signed.
   */
  async estimateUsageWithJwt(
    bearerToken: string,
    userId: string,
    serviceId: string,
    estimatedUnits: number
  ): Promise<UsageEstimateResult | null> {
    return estimateUsageWithJwt({
      baseUrl: this.apiOrigin,
      bearerToken,
      userId,
      serviceId,
      estimatedUnits,
      fetch: this.fetchFn,
    });
  }

  /**
   * Gateway service invoke (sync or async). See platform spec §1.1–1.2.
   */
  async invokeService(
    method: GatewayHttpMethod,
    serviceId: string,
    endpointId: string,
    serviceKey: string,
    options?: { body?: string | null; async?: boolean }
  ): Promise<GatewayInvokeResult | null> {
    return invokeService({
      baseUrl: this.apiOrigin,
      method,
      serviceId,
      endpointId,
      serviceKey,
      body: options?.body ?? null,
      async: options?.async ?? false,
      fetch: this.fetchFn,
    });
  }

  async reportUsage(userId: string, serviceId: string, unitsUsed: number): Promise<UsageReportResponse> {
    return reportUsage({
      baseUrl: this.apiOrigin,
      userId,
      serviceId,
      unitsUsed,
      serviceSecret: this.serviceSecret,
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
      serviceSecret: this.serviceSecret,
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
        serviceSecret: this.serviceSecret,
        fetch: this.fetchFn,
      });
    }
    return reportCompletion({
      callbackUrl,
      requestId,
      status,
      units,
      serviceSecret: this.serviceSecret,
      fetch: this.fetchFn,
    });
  }

  async getRequestStatus(requestId: string, serviceKey: string): Promise<GatewayPollResult> {
    return getRequestStatus({
      baseUrl: this.apiOrigin,
      requestId,
      serviceKey,
      fetch: this.fetchFn,
    });
  }

  async getRequestResult(requestId: string, serviceKey: string): Promise<GatewayPollResult> {
    return getRequestResult({
      baseUrl: this.apiOrigin,
      requestId,
      serviceKey,
      fetch: this.fetchFn,
    });
  }
}
