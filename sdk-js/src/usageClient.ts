import { TollaraHeaders } from './tollaraHeaders';
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
  if (url == null || typeof url !== 'string') {
    return { baseUrl: '', signature: null, timestamp: null };
  }
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

export type UsageCallbackResult = {
  success: boolean;
  httpStatus: number;
  httpStatusText: string;
  requestUrl: string;
  responseBody?: string;
  networkError?: string;
};

async function postSignedUsageCallback(
  urlWithQuery: string,
  bodyString: string,
  serviceSecret: string,
  fetchFn: typeof globalThis.fetch,
): Promise<UsageCallbackResult> {
  const { baseUrl, timestamp } = parseUrlParams(urlWithQuery);
  if (!timestamp) {
    return {
      success: false,
      httpStatus: 0,
      httpStatusText: urlWithQuery ? 'Missing timestamp query parameter in URL' : 'Missing or invalid callback/progress URL',
      requestUrl: baseUrl,
    };
  }

  const signature = calculateHmacWithTimestamp(bodyString, timestamp, serviceSecret);

  try {
    const res = await fetchFn(baseUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        [TollaraHeaders.SIGNATURE]: signature,
        [TollaraHeaders.TIMESTAMP]: timestamp,
      },
      body: bodyString,
    });
    const responseBody = await res.text();
    return {
      success: res.ok,
      httpStatus: res.status,
      httpStatusText: res.statusText,
      requestUrl: baseUrl,
      responseBody: responseBody || undefined,
    };
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    return {
      success: false,
      httpStatus: 0,
      httpStatusText: 'Network error',
      requestUrl: baseUrl,
      networkError: message,
    };
  }
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

/** POST to progressUrl with signed body (optional errorMessage). */
export async function reportProgress(params: ReportProgressParams): Promise<UsageCallbackResult> {
  const { progressUrl, stage, percentageComplete, errorMessage, serviceSecret, fetch: fetchFn = fetch } = params;

  const body: Record<string, unknown> = {
    stage,
    percentageComplete,
    timestamp: new Date().toISOString(),
  };
  if (errorMessage != null) body.errorMessage = errorMessage;

  return postSignedUsageCallback(progressUrl, JSON.stringify(body), serviceSecret, fetchFn);
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

/** POST to callbackUrl with signed completion body. */
export async function reportCompletion(params: ReportCompletionParams): Promise<UsageCallbackResult> {
  const { callbackUrl, status, result, resultUrl, contentType, units, serviceSecret, fetch: fetchFn = fetch } = params;

  const body: Record<string, unknown> = {
    status: status as string,
    timestamp: new Date().toISOString(),
    units: units ?? 0,
  };
  if (result != null) body.result = result;
  if (resultUrl != null) body.resultUrl = resultUrl;
  if (contentType != null) body.contentType = contentType;

  return postSignedUsageCallback(callbackUrl, JSON.stringify(body), serviceSecret, fetchFn);
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
    /** API origin; defaults to `https://api.tollara.ai`. */
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
      [TollaraHeaders.SIGNATURE]: signature,
      [TollaraHeaders.TIMESTAMP]: epochSec,
    },
    body: bodyString,
  });

  if (!res.ok) {
    throw new Error(`Usage report failed: ${res.status} ${res.statusText}`);
  }
  return (await res.json()) as UsageReportResponse;
}
