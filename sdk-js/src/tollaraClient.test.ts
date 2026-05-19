import { TollaraClient, DEFAULT_API_URL, ENV_API_URL, ENV_SERVICE_SECRET } from './tollaraClient';
import { calculateHmac } from './hmac';

const SERVICE_ID = '550e8400-e29b-41d4-a716-446655440000';
const SERVICE_SECRET = 'test-service-secret';
const SERVICE_KEY = 'k';

describe('TollaraClient', () => {
  it('uses default production apiUrl when omitted', async () => {
    const mockFetch = jest.fn(async (input: string | Request | URL) => {
      const u = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
      expect(u).toBe(`${DEFAULT_API_URL}/api/requests/r1/status`);
      return new Response('{}', { status: 200 });
    });
    const client = new TollaraClient({
      serviceId: SERVICE_ID,
      serviceSecret: SERVICE_SECRET,
      fetch: mockFetch as unknown as typeof fetch,
    });
    await client.getRequestStatus('r1', SERVICE_KEY);
  });

  it('throws without secret', () => {
    expect(() => new TollaraClient({ apiUrl: 'http://x' })).toThrow(/secret/);
  });

  it('getRequestStatus uses default gateway prefix', async () => {
    const base = 'http://localhost:58888';
    const mockFetch = jest.fn(async (input: string | Request | URL) => {
      const u = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
      expect(u).toBe(`${base}/api/requests/job-1/status`);
      return new Response('{"state":"OK"}', { status: 200 });
    });
    const client = new TollaraClient({
      apiUrl: base,
      serviceId: SERVICE_ID,
      serviceSecret: SERVICE_SECRET,
      fetch: mockFetch as unknown as typeof fetch,
    });
    const res = await client.getRequestStatus('job-1', SERVICE_KEY);
    expect(res.ok).toBe(true);
    expect(res.body).toContain('OK');
  });

  it('reportUsage uses default usage prefix', async () => {
    const base = 'http://localhost:58889';
    const mockFetch = jest.fn(async (input: string | Request | URL) => {
      const u = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
      expect(u).toBe(`${base}/api/usage/report`);
      return new Response(
        JSON.stringify({
          status: 'ok',
          isOverLimit: false,
          remainingRequestsPerPeriod: 1,
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      );
    });
    const client = new TollaraClient({
      apiUrl: base,
      serviceId: SERVICE_ID,
      serviceSecret: SERVICE_SECRET,
      fetch: mockFetch as unknown as typeof fetch,
    });
    const out = await client.reportUsage('user-1', SERVICE_ID, 1);
    expect(out.status).toBe('ok');
  });

  it('validateServiceKey uses default core prefix and returns parsed result', async () => {
    const base = 'http://localhost:58891';
    const responseBody = JSON.stringify({
      valid: true,
      userId: 'user-1',
      serviceId: SERVICE_ID,
      plan: 'basic',
      roles: ['user'],
      quotaRemaining: 5,
      subscriptionActive: true,
    });
    const ts = '1700000000';
    const signature = calculateHmac(responseBody + ts, SERVICE_SECRET);

    const mockFetch = jest.fn(async (input: string | Request | URL) => {
      const u = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
      expect(u).toBe(`${base}/api/v1/service-keys/validate`);
      return new Response(responseBody, {
        status: 200,
        headers: {
          'X-Tollara-Signature': signature,
          'X-Tollara-Timestamp': ts,
        },
      });
    });

    const client = new TollaraClient({
      apiUrl: base,
      serviceId: SERVICE_ID,
      serviceSecret: SERVICE_SECRET,
      fetch: mockFetch as unknown as typeof fetch,
    });
    const out = await client.validateServiceKey('service-key-1');
    expect(out).not.toBeNull();
    expect(out?.userId).toBe('user-1');
  });

  it('reads apiUrl and secret from process.env when omitted', async () => {
    const prevUrl = process.env[ENV_API_URL];
    const prevSecret = process.env[ENV_SERVICE_SECRET];
    const base = 'http://env-js.test';
    process.env[ENV_API_URL] = base;
    process.env[ENV_SERVICE_SECRET] = SERVICE_SECRET;
    process.env.TOLLARA_SERVICE_ID = SERVICE_ID;

    const mockFetch = jest.fn(async () => new Response('{}', { status: 200 }));
    try {
      const client = new TollaraClient({ fetch: mockFetch as unknown as typeof fetch });
      await client.getRequestStatus('r1', SERVICE_KEY);
      expect(mockFetch).toHaveBeenCalledWith(
        `${base}/api/requests/r1/status`,
        expect.objectContaining({ method: 'GET' })
      );
    } finally {
      if (prevUrl === undefined) delete process.env[ENV_API_URL];
      else process.env[ENV_API_URL] = prevUrl;
      if (prevSecret === undefined) delete process.env[ENV_SERVICE_SECRET];
      else process.env[ENV_SERVICE_SECRET] = prevSecret;
      delete process.env.TOLLARA_SERVICE_ID;
    }
  });
});
