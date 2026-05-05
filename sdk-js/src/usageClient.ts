import { AgentVendHeaders } from './agentVendHeaders';
import { CompletionStatus } from './completionStatus';
import { DEFAULT_API_URL, DEFAULT_USAGE_PATH_PREFIX } from './constants';
import { calculateHmacWithTimestamp } from './hmac';
import { resolveBaseUrl } from './urls';

function usageReportInstantAndEpochSeconds(timestamp?: number | Date | null): { iso: string; epochSec: string } {
  let ms: number;
  if (timestamp == null) {
    ms = Date.now();
  } else if (timestamp instanceof Date) {
    ms = timestamp.getTime();
  } else if (typeof timestamp === 'number') {
    ms = timestamp < 1e11 ? Math.round(timestamp * 1000) : timestamp;
  } else {
    ms = Date.now();
  }
  const sec = Math.floor(ms / 1000);
  return { iso: new Date(ms).toISOString(), epochSec: String(sec) };
}

export { DEFAULT_USAGE_PATH_PREFIX } from './constants';

/** Builds `{baseUrl}/api/usage/report` using the default usage path. */
export function buildUsageReportUrl(baseUrl: string): string {
  const base = resolveBaseUrl(baseUrl, DEFAULT_API_URL);
  let p = DEFAULT_USAGE_PATH_PREFIX.trim();
  if (!p.startsWith('/')) p = `/${p}`;
  p = p.replace(/\/$/, '');
  return `${base}${p}/report`;
}

function parseUrlParams(url: string): { baseUrl: string; signature: string | null; timestamp: string | null } {
  let baseUrl = url;
  let signature: string | null = null;
  let timestamp: string | null = null;
  const q = url.indexOf('?');
  if (q >= 0) {
    baseUrl = url.slice(0, q);
    const search = new URLSearchParams(url.slice(q + 1));
    signature = search.get('signature');
    timestamp = search.get('timestamp');
  }
  return { baseUrl, signature, timestamp };
}

export type ReportProgressParams = {
  progressUrl: string;
  requestId: string;
  stage: string;
  percentageComplete: number;
  /** Omit or leave unset when there is no error (avoids passing null). */
  errorMessage?: string | null;
  serviceSecret: string;
  fetch?: typeof globalThis.fetch;
};

/**
 * POST to progressUrl with signed body (optional errorMessage).
 */
export async function reportProgress(params: ReportProgressParams): Promise<boolean> {
  const { progressUrl, requestId, stage, percentageComplete, errorMessage, serviceSecret, fetch: fetchFn = fetch } = params;
  const { baseUrl, timestamp } = parseUrlParams(progressUrl);
  if (!timestamp) return false;

  const body: Record<string, unknown> = {
    stage,
    percentageComplete,
    timestamp: new Date().toISOString(),
  };
  if (errorMessage != null) body.errorMessage = errorMessage;

  const bodyString = JSON.stringify(body);
  const signature = calculateHmacWithTimestamp(bodyString, timestamp, serviceSecret);

  try {
    const res = await fetchFn(baseUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        [AgentVendHeaders.SIGNATURE]: signature,
        [AgentVendHeaders.TIMESTAMP]: timestamp,
      },
      body: bodyString,
    });
    return res.ok;
  } catch {
    return false;
  }
}

export type ReportCompletionParams = {
  callbackUrl: string;
  requestId: string;
  status: CompletionStatus;
  result?: string | null;
  resultUrl?: string | null;
  contentType?: string | null;
  units?: number | null;
  serviceSecret: string;
  fetch?: typeof globalThis.fetch;
};

/**
 * POST completion with status and optional units (defaults to 0).
 */
export async function reportCompletion(
  params: Pick<ReportCompletionParams, 'callbackUrl' | 'requestId' | 'status' | 'serviceSecret' | 'fetch'> & {
    units?: number | null;
  }
): Promise<boolean> {
  return reportCompletionFull({ ...params, units: params.units ?? 0 });
}

/**
 * POST completion with inline result text.
 */
export async function reportCompletionWithResult(
  params: Pick<ReportCompletionParams, 'callbackUrl' | 'requestId' | 'status' | 'result' | 'units' | 'serviceSecret' | 'fetch'>
): Promise<boolean> {
  return reportCompletionFull({ ...params, units: params.units ?? 0 });
}

/**
 * POST to callbackUrl with signed body (all optional fields).
 */
export async function reportCompletionFull(params: ReportCompletionParams): Promise<boolean> {
  const { callbackUrl, status, result, resultUrl, contentType, units, serviceSecret, fetch: fetchFn = fetch } = params;
  const { baseUrl, timestamp } = parseUrlParams(callbackUrl);
  if (!timestamp) return false;

  const body: Record<string, unknown> = {
    status: status as string,
    timestamp: new Date().toISOString(),
    units: units ?? 0,
  };
  if (result != null) body.result = result;
  if (resultUrl != null) body.resultUrl = resultUrl;
  if (contentType != null) body.contentType = contentType;

  const bodyString = JSON.stringify(body);
  const signature = calculateHmacWithTimestamp(bodyString, timestamp, serviceSecret);

  try {
    const res = await fetchFn(baseUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        [AgentVendHeaders.SIGNATURE]: signature,
        [AgentVendHeaders.TIMESTAMP]: timestamp,
      },
      body: bodyString,
    });
    return res.ok;
  } catch {
    return false;
  }
}

export interface UsageReportResponse {
  status?: string;
  warning?: string;
  isOverLimit?: boolean;
  remainingRequestsPerPeriod?: number;
  remainingTimeUnitsPerPeriod?: number;
  remainingSpendingCap?: number;
  overageRate?: number;
}

/**
 * POST usage report with signed body (`{baseUrl}/api/usage/report`).
 */
export async function reportUsage(
  params: {
    /** API origin; defaults to `https://api.agentvend.api`. */
    baseUrl?: string | null;
    userId: string;
    serviceId: string;
    unitsUsed: number;
    timestamp?: number | Date | null;
    serviceSecret: string;
    fetch?: typeof globalThis.fetch;
  }
): Promise<UsageReportResponse> {
  const {
    baseUrl,
    userId,
    serviceId,
    unitsUsed,
    timestamp,
    serviceSecret,
    fetch: fetchFn = fetch,
  } = params;
  const { iso, epochSec } = usageReportInstantAndEpochSeconds(timestamp ?? null);

  const body = { userId, serviceId, unitsUsed, timestamp: iso };
  const bodyString = JSON.stringify(body);
  const signature = calculateHmacWithTimestamp(bodyString, epochSec, serviceSecret);

  const url = buildUsageReportUrl(baseUrl ?? DEFAULT_API_URL);

  const res = await fetchFn(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      [AgentVendHeaders.SIGNATURE]: signature,
      [AgentVendHeaders.TIMESTAMP]: epochSec,
    },
    body: bodyString,
  });

  if (!res.ok) {
    throw new Error(`Usage report failed: ${res.status} ${res.statusText}`);
  }
  return (await res.json()) as UsageReportResponse;
}
