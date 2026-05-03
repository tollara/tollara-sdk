import { AgentVendHeaders } from './agentVendHeaders';
import { CompletionStatus } from './completionStatus';
import { calculateHmacWithTimestamp } from './hmac';
import {
  buildUsageReportUrl,
  DEFAULT_USAGE_PATH_PREFIX,
  reportCompletion,
  reportCompletionWithResult,
  reportProgress,
  reportUsage,
} from './usageClient';

describe('usageClient', () => {
  describe('buildUsageReportUrl', () => {
    it('uses default prefix /api/usage', () => {
      expect(buildUsageReportUrl('http://u.test')).toBe('http://u.test/api/usage/report');
      expect(buildUsageReportUrl('http://u.test/')).toBe('http://u.test/api/usage/report');
    });

    it('exports default constant', () => {
      expect(DEFAULT_USAGE_PATH_PREFIX).toBe('/api/usage');
    });
  });

  it('reportUsage posts to default usage report path', async () => {
    const calls: string[] = [];
    const mockFetch: typeof fetch = async (input, init) => {
      const url =
        typeof input === 'string' ? input : input instanceof URL ? input.href : (input as Request).url;
      calls.push(url);
      expect(init?.method).toBe('POST');
      expect(init?.headers).toEqual(
        expect.objectContaining({
          'Content-Type': 'application/json',
          [AgentVendHeaders.SIGNATURE]: expect.any(String),
          [AgentVendHeaders.TIMESTAMP]: expect.any(String),
        })
      );
      return new Response(
        JSON.stringify({
          status: 'ok',
          isOverLimit: false,
          remainingRequestsPerPeriod: 1,
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      );
    };

    await reportUsage({
      baseUrl: 'http://u.test',
      userId: 'u1',
      agentId: 'a1',
      unitsUsed: 2,
      agentSecret: 's',
      fetch: mockFetch,
    });

    expect(calls).toEqual(['http://u.test/api/usage/report']);
  });

  it('reportUsage signs payload with explicit timestamp', async () => {
    const fetchMock = jest.fn(async (_input: string | URL | Request, init?: RequestInit) => {
      const rawBody = String(init?.body);
      const timestamp = '1700000000';
      const expectedSig = calculateHmacWithTimestamp(rawBody, timestamp, 'secret');
      expect(init?.headers).toEqual(
        expect.objectContaining({
          [AgentVendHeaders.TIMESTAMP]: timestamp,
          [AgentVendHeaders.SIGNATURE]: expectedSig,
        })
      );
      const parsed = JSON.parse(rawBody) as { userId: string; agentId: string; unitsUsed: number; timestamp: string };
      expect(parsed.userId).toBe('u1');
      expect(parsed.agentId).toBe('a1');
      expect(parsed.unitsUsed).toBe(3);
      expect(parsed.timestamp).toBe(new Date(1700000000 * 1000).toISOString());
      return new Response(
        JSON.stringify({ status: 'ok', isOverLimit: false, remainingRequestsPerPeriod: 10 }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      );
    });

    await reportUsage({
      baseUrl: 'http://u.test',
      userId: 'u1',
      agentId: 'a1',
      unitsUsed: 3,
      timestamp: 1700000000,
      agentSecret: 'secret',
      fetch: fetchMock as unknown as typeof fetch,
    });
  });

  it('reportUsage throws on non-2xx', async () => {
    const mockFetch: typeof fetch = async () => new Response('nope', { status: 500, statusText: 'Internal' });
    await expect(
      reportUsage({
        baseUrl: 'http://u.test',
        userId: 'u1',
        agentId: 'a1',
        unitsUsed: 1,
        agentSecret: 'secret',
        fetch: mockFetch,
      })
    ).rejects.toThrow(/Usage report failed: 500 Internal/);
  });

  it('reportProgress signs and posts when timestamp is in URL', async () => {
    const progressUrl = 'http://u.test/api/usage/progress/req-1?signature=ignored&timestamp=1700000000';
    const fetchMock = jest.fn(async (_input: string | URL | Request, init?: RequestInit) => {
      const rawBody = String(init?.body);
      const expectedSig = calculateHmacWithTimestamp(rawBody, '1700000000', 'secret');
      expect(init?.headers).toEqual(
        expect.objectContaining({
          [AgentVendHeaders.TIMESTAMP]: '1700000000',
          [AgentVendHeaders.SIGNATURE]: expectedSig,
        })
      );
      return new Response('', { status: 200 });
    });

    const ok = await reportProgress({
      progressUrl,
      requestId: 'req-1',
      stage: 'processing',
      percentageComplete: 50,
      agentSecret: 'secret',
      fetch: fetchMock as unknown as typeof fetch,
    });

    expect(ok).toBe(true);
    expect(fetchMock).toHaveBeenCalledWith(
      'http://u.test/api/usage/progress/req-1',
      expect.objectContaining({ method: 'POST' })
    );
  });

  it('reportCompletionWithResult signs and posts callback payload', async () => {
    const callbackUrl = 'http://u.test/api/usage/complete/req-2?timestamp=1700000001';
    const fetchMock = jest.fn(async (_input: string | URL | Request, init?: RequestInit) => {
      const rawBody = String(init?.body);
      const expectedSig = calculateHmacWithTimestamp(rawBody, '1700000001', 'secret');
      expect(init?.headers).toEqual(
        expect.objectContaining({
          [AgentVendHeaders.TIMESTAMP]: '1700000001',
          [AgentVendHeaders.SIGNATURE]: expectedSig,
        })
      );
      const parsed = JSON.parse(rawBody);
      expect(parsed.status).toBe('COMPLETED');
      expect(parsed.result).toBe('done');
      expect(parsed.units).toBe(1);
      return new Response('', { status: 200 });
    });

    const ok = await reportCompletionWithResult({
      callbackUrl,
      requestId: 'req-2',
      status: CompletionStatus.Completed,
      result: 'done',
      units: 1,
      agentSecret: 'secret',
      fetch: fetchMock as unknown as typeof fetch,
    });
    expect(ok).toBe(true);
  });

  it('reportCompletion returns false when callback URL has no timestamp', async () => {
    const ok = await reportCompletion({
      callbackUrl: 'http://u.test/api/usage/complete/req-3',
      requestId: 'req-3',
      status: CompletionStatus.Failed,
      agentSecret: 'secret',
      fetch: jest.fn() as unknown as typeof fetch,
    });
    expect(ok).toBe(false);
  });
});
