import { AgentVendClient, DEFAULT_API_URL, ENV_API_URL, ENV_AGENT_SECRET } from './agentVendClient';

const AGENT_ID = '550e8400-e29b-41d4-a716-446655440000';
const AGENT_SECRET = 'test-agent-secret';
const AGENT_KEY = 'k';

describe('AgentVendClient', () => {
  it('uses default production apiUrl when omitted', async () => {
    const mockFetch = jest.fn(async (input: string | Request | URL) => {
      const u = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
      expect(u).toBe(`${DEFAULT_API_URL}/api/requests/r1/status`);
      return new Response('{}', { status: 200 });
    });
    const client = new AgentVendClient({
      agentId: AGENT_ID,
      agentSecret: AGENT_SECRET,
      fetch: mockFetch as unknown as typeof fetch,
    });
    await client.getRequestStatus('r1', AGENT_KEY);
  });

  it('throws without secret', () => {
    expect(() => new AgentVendClient({ apiUrl: 'http://x' })).toThrow(/secret/);
  });

  it('getRequestStatus uses default gateway prefix', async () => {
    const base = 'http://localhost:58888';
    const mockFetch = jest.fn(async (input: string | Request | URL) => {
      const u = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
      expect(u).toBe(`${base}/api/requests/job-1/status`);
      return new Response('{"state":"OK"}', { status: 200 });
    });
    const client = new AgentVendClient({
      apiUrl: base,
      agentId: AGENT_ID,
      agentSecret: AGENT_SECRET,
      fetch: mockFetch as unknown as typeof fetch,
    });
    const res = await client.getRequestStatus('job-1', AGENT_KEY);
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
    const client = new AgentVendClient({
      apiUrl: base,
      agentId: AGENT_ID,
      agentSecret: AGENT_SECRET,
      fetch: mockFetch as unknown as typeof fetch,
    });
    const out = await client.reportUsage('user-1', AGENT_ID, 1);
    expect(out.status).toBe('ok');
  });

  it('custom usagePathPrefix is used for report', async () => {
    const base = 'http://localhost:58890';
    const mockFetch = jest.fn(async (input: string | Request | URL) => {
      const u = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
      expect(u).toBe(`${base}/usage/api/v1/report`);
      return new Response(
        JSON.stringify({
          status: 'ok',
          isOverLimit: false,
          remainingRequestsPerPeriod: 1,
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      );
    });
    const client = new AgentVendClient({
      apiUrl: base,
      agentId: AGENT_ID,
      agentSecret: AGENT_SECRET,
      usagePathPrefix: '/usage/api/v1',
      fetch: mockFetch as unknown as typeof fetch,
    });
    await client.reportUsage('user-1', AGENT_ID, 1);
  });

  it('reads apiUrl and secret from process.env when omitted', async () => {
    const prevUrl = process.env[ENV_API_URL];
    const prevSecret = process.env[ENV_AGENT_SECRET];
    const base = 'http://env-js.test';
    process.env[ENV_API_URL] = base;
    process.env[ENV_AGENT_SECRET] = AGENT_SECRET;
    process.env.AGENTVEND_AGENT_ID = AGENT_ID;

    const mockFetch = jest.fn(async () => new Response('{}', { status: 200 }));
    try {
      const client = new AgentVendClient({ fetch: mockFetch as unknown as typeof fetch });
      await client.getRequestStatus('r1', AGENT_KEY);
      expect(mockFetch).toHaveBeenCalledWith(
        `${base}/api/requests/r1/status`,
        expect.objectContaining({ method: 'GET' })
      );
    } finally {
      if (prevUrl === undefined) delete process.env[ENV_API_URL];
      else process.env[ENV_API_URL] = prevUrl;
      if (prevSecret === undefined) delete process.env[ENV_AGENT_SECRET];
      else process.env[ENV_AGENT_SECRET] = prevSecret;
      delete process.env.AGENTVEND_AGENT_ID;
    }
  });
});
