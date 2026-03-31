import { getRequestResult, getRequestStatus } from './gatewayClient';

describe('gatewayClient', () => {
  it('getRequestStatus calls GET with Bearer', async () => {
    const fetchMock = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      text: async () => '{"state":"PENDING"}',
    });
    const r = await getRequestStatus({
      gatewayBaseUrl: 'https://gw.example.com',
      gatewayPathPrefix: '/api',
      requestId: 'job-1',
      agentKey: 'key-abc',
      fetch: fetchMock as unknown as typeof fetch,
    });
    expect(r.ok).toBe(true);
    expect(r.status).toBe(200);
    expect(r.body).toContain('PENDING');
    expect(fetchMock).toHaveBeenCalledWith(
      'https://gw.example.com/api/requests/job-1/status',
      expect.objectContaining({
        method: 'GET',
        headers: { Authorization: 'Bearer key-abc' },
      })
    );
  });

  it('getRequestResult builds ecs-style path', async () => {
    const fetchMock = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      text: async () => '{}',
    });
    await getRequestResult({
      gatewayBaseUrl: 'https://gw.example.com/',
      gatewayPathPrefix: '/gateway/api/v1',
      requestId: 'r2',
      agentKey: 'k',
      fetch: fetchMock as unknown as typeof fetch,
    });
    expect(fetchMock).toHaveBeenCalledWith(
      'https://gw.example.com/gateway/api/v1/requests/r2/result',
      expect.any(Object)
    );
  });
});
