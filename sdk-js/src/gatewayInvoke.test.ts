import { invokeAgent } from './gatewayInvoke';

const BASE = 'http://gw.test';
const AGENT_KEY = 'k';

describe('gatewayInvoke', () => {
  it('POSTs sync invoke to /api/agent/{agentId}/endpoint/{endpointId}/invoke', async () => {
    const fetchMock = jest.fn(async (input: string | URL | Request, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
      expect(url).toBe(`${BASE}/api/agent/a1/endpoint/e1/invoke`);
      expect(init?.method).toBe('POST');
      expect(init?.headers).toEqual(
        expect.objectContaining({
          Authorization: `Bearer ${AGENT_KEY}`,
          'Content-Type': 'application/json',
        })
      );
      expect(init?.body).toBe('{}');
      return new Response('{}', { status: 200 });
    });
    const r = await invokeAgent({
      baseUrl: BASE,
      method: 'POST',
      agentId: 'a1',
      endpointId: 'e1',
      agentKey: AGENT_KEY,
      body: '{}',
      fetch: fetchMock as unknown as typeof fetch,
    });
    expect(r?.statusCode).toBe(200);
  });

  it('parses async envelope on 202', async () => {
    const body = JSON.stringify({
      status: 'ACCEPTED',
      requestId: 'r1',
      callbackUrl: 'https://u/c',
      progressUrl: 'https://g/p',
    });
    const fetchMock: typeof fetch = async () =>
      new Response(body, {
        status: 202,
        headers: { 'Content-Type': 'application/json' },
      });
    const r = await invokeAgent({
      baseUrl: BASE,
      method: 'POST',
      agentId: 'a',
      endpointId: 'e',
      agentKey: AGENT_KEY,
      body: '{}',
      async: true,
      fetch: fetchMock,
    });
    expect(r?.asyncEnvelope?.requestId).toBe('r1');
    expect(r?.asyncEnvelope?.callbackUrl).toBe('https://u/c');
    expect(r?.asyncEnvelope?.progressUrl).toBe('https://g/p');
  });
});
