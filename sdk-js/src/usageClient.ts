import { AgentVendHeaders } from './agentVendHeaders';
import { CompletionStatus } from './completionStatus';
import { calculateHmacWithTimestamp } from './hmac';

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
  agentSecret: string;
  fetch?: typeof globalThis.fetch;
};

/**
 * POST to progressUrl with signed body (optional errorMessage).
 */
export async function reportProgress(params: ReportProgressParams): Promise<boolean> {
  const { progressUrl, requestId, stage, percentageComplete, errorMessage, agentSecret, fetch: fetchFn = fetch } = params;
  const { baseUrl, timestamp } = parseUrlParams(progressUrl);
  if (!timestamp) return false;

  const body: Record<string, unknown> = {
    stage,
    percentageComplete,
    timestamp: new Date().toISOString(),
  };
  if (errorMessage != null) body.errorMessage = errorMessage;

  const bodyString = JSON.stringify(body);
  const signature = calculateHmacWithTimestamp(bodyString, timestamp, agentSecret);

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
  agentSecret: string;
  fetch?: typeof globalThis.fetch;
};

/**
 * POST completion with status and optional units (defaults to 0).
 */
export async function reportCompletion(
  params: Pick<ReportCompletionParams, 'callbackUrl' | 'requestId' | 'status' | 'agentSecret' | 'fetch'> & {
    units?: number | null;
  }
): Promise<boolean> {
  return reportCompletionFull({ ...params, units: params.units ?? 0 });
}

/**
 * POST completion with inline result text.
 */
export async function reportCompletionWithResult(
  params: Pick<ReportCompletionParams, 'callbackUrl' | 'requestId' | 'status' | 'result' | 'units' | 'agentSecret' | 'fetch'>
): Promise<boolean> {
  return reportCompletionFull({ ...params, units: params.units ?? 0 });
}

/**
 * POST to callbackUrl with signed body (all optional fields).
 */
export async function reportCompletionFull(params: ReportCompletionParams): Promise<boolean> {
  const { callbackUrl, status, result, resultUrl, contentType, units, agentSecret, fetch: fetchFn = fetch } = params;
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
  const signature = calculateHmacWithTimestamp(bodyString, timestamp, agentSecret);

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
 * POST to usage service /api/usage/report with signed body.
 */
export async function reportUsage(
  params: {
    usageServiceUrl: string;
    userId: string;
    agentId: string;
    unitsUsed: number;
    timestamp?: number | Date | null;
    agentSecret: string;
    fetch?: typeof globalThis.fetch;
  }
): Promise<UsageReportResponse> {
  const { usageServiceUrl, userId, agentId, unitsUsed, timestamp, agentSecret, fetch: fetchFn = fetch } = params;
  const ts = timestamp != null
    ? (timestamp instanceof Date ? timestamp.getTime() : timestamp)
    : Date.now();

  const body = { userId, agentId, unitsUsed, timestamp: ts };
  const bodyString = JSON.stringify(body);
  const timestampStr = String(ts);
  const signature = calculateHmacWithTimestamp(bodyString, timestampStr, agentSecret);

  const base = usageServiceUrl.replace(/\/$/, '');
  const url = `${base}/api/usage/report`;

  const res = await fetchFn(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      [AgentVendHeaders.SIGNATURE]: signature,
      [AgentVendHeaders.TIMESTAMP]: timestampStr,
    },
    body: bodyString,
  });

  if (!res.ok) {
    throw new Error(`Usage report failed: ${res.status} ${res.statusText}`);
  }
  return (await res.json()) as UsageReportResponse;
}
