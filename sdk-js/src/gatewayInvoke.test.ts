import { invokeService } from './gatewayInvoke';

const BASE = 'http://gw.test';
const SERVICE_KEY = 'k';

describe('gatewayInvoke', () => {
  it('POSTs sync invoke to /api/service/{serviceId}/endpoint/{endpointId}/invoke', async () => {
    const fetchMock = jest.fn(async (input: string | URL | Request, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
      expect(url).toBe(`${BASE}/api/service/s1/endpoint/e1/invoke`);
      expect(init?.method).toBe('POST');
      expect(init?.headers).toEqual(
        expect.objectContaining({
          Authorization: `Bearer ${SERVICE_KEY}`,
          'Content-Type': 'application/json',
        })
      );
      expect(init?.body).toBe('{}');
      return new Response('{}', { status: 200 });
    });
    const r = await invokeService({
      baseUrl: BASE,
      method: 'POST',
      serviceId: 's1',
      endpointId: 'e1',
      serviceKey: SERVICE_KEY,
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
    const r = await invokeService({
      baseUrl: BASE,
      method: 'POST',
      serviceId: 's',
      endpointId: 'e',
      serviceKey: SERVICE_KEY,
      body: '{}',
      async: true,
      fetch: fetchMock,
    });
    expect(r?.asyncEnvelope?.requestId).toBe('r1');
    expect(r?.asyncEnvelope?.callbackUrl).toBe('https://u/c');
    expect(r?.asyncEnvelope?.progressUrl).toBe('https://g/p');
  });

  it('uses ECS gateway path prefix for hosted api.tollara.ai origin', async () => {
    const fetchMock = jest.fn(async (input: string | URL | Request) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
      expect(url).toBe(
        'https://api.tollara.ai/gateway/api/v1/service/s1/endpoint/e1/invoke/async',
      );
      return new Response('{}', { status: 500 });
    });
    await invokeService({
      baseUrl: 'https://api.tollara.ai',
      method: 'POST',
      serviceId: 's1',
      endpointId: 'e1',
      serviceKey: SERVICE_KEY,
      body: '{}',
      async: true,
      fetch: fetchMock as unknown as typeof fetch,
    });
  });
});
