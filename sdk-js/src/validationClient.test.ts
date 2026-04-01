import { AgentVendHeaders } from './agentVendHeaders';
import { calculateHmac } from './hmac';
import { createValidationCache, validateAgentKey } from './validationClient';

const CORE_BASE = 'http://core.test';
const AGENT_ID = '550e8400-e29b-41d4-a716-446655440000';
const AGENT_SECRET = 'test-agent-secret';

describe('validationClient', () => {
  it('returns parsed validation result for signed valid response', async () => {
    const responseBody = JSON.stringify({
      valid: true,
      userId: 'user-123',
      agentId: AGENT_ID,
      plan: 'basic',
      roles: ['user'],
      quotaRemaining: 100,
      subscriptionActive: true,
      billingModelType: null,
      measurementType: null,
      unitLabel: null,
      timestamp: 1700000000,
      error: null,
    });
    const timestamp = '1700000000';
    const signature = calculateHmac(responseBody + timestamp, AGENT_SECRET);

    const fetchMock: typeof fetch = async () =>
      new Response(responseBody, {
        status: 200,
        headers: {
          'Content-Type': 'application/json',
          [AgentVendHeaders.SIGNATURE]: signature,
          [AgentVendHeaders.TIMESTAMP]: timestamp,
        },
      });

    const result = await validateAgentKey({
      baseUrl: CORE_BASE,
      agentKey: 'bearer-token',
      agentId: AGENT_ID,
      agentSecret: AGENT_SECRET,
      fetch: fetchMock,
    });

    expect(result).toEqual({
      userId: 'user-123',
      agentId: AGENT_ID,
      plan: 'basic',
      roles: ['user'],
      quotaRemaining: 100,
      subscriptionActive: true,
      billingModelType: null,
      measurementType: null,
      unitLabel: null,
    });
  });

  it('returns null when agent key is blank', async () => {
    const fetchMock = jest.fn();
    const result = await validateAgentKey({
      baseUrl: CORE_BASE,
      agentKey: '   ',
      agentId: AGENT_ID,
      agentSecret: AGENT_SECRET,
      fetch: fetchMock as unknown as typeof fetch,
    });
    expect(result).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('returns null when response is not ok', async () => {
    const fetchMock: typeof fetch = async () => new Response('unauthorized', { status: 401 });
    const result = await validateAgentKey({
      baseUrl: CORE_BASE,
      agentKey: 'bad',
      agentId: AGENT_ID,
      agentSecret: AGENT_SECRET,
      fetch: fetchMock,
    });
    expect(result).toBeNull();
  });

  it('returns null when signature is invalid', async () => {
    const responseBody = JSON.stringify({ valid: true, userId: 'u1' });
    const fetchMock: typeof fetch = async () =>
      new Response(responseBody, {
        status: 200,
        headers: {
          [AgentVendHeaders.SIGNATURE]: 'bad-signature',
          [AgentVendHeaders.TIMESTAMP]: '1700000000',
        },
      });
    const result = await validateAgentKey({
      baseUrl: CORE_BASE,
      agentKey: 'k',
      agentId: AGENT_ID,
      agentSecret: AGENT_SECRET,
      fetch: fetchMock,
    });
    expect(result).toBeNull();
  });

  it('posts JSON body to /agent-keys/validate', async () => {
    const responseBody = JSON.stringify({ valid: false });
    const timestamp = '1700000000';
    const signature = calculateHmac(responseBody + timestamp, AGENT_SECRET);
    const fetchMock = jest.fn(async (input: string | URL | Request, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
      expect(url).toBe(`${CORE_BASE}/api/v1/agent-keys/validate`);
      expect(init?.method).toBe('POST');
      expect(init?.headers).toEqual({ 'Content-Type': 'application/json' });
      expect(JSON.parse(String(init?.body))).toEqual({
        agentKey: 'the-agent-key',
        agentId: AGENT_ID,
        agentSecret: AGENT_SECRET,
      });
      return new Response(responseBody, {
        status: 200,
        headers: {
          [AgentVendHeaders.SIGNATURE]: signature,
          [AgentVendHeaders.TIMESTAMP]: timestamp,
        },
      });
    });

    const result = await validateAgentKey({
      baseUrl: CORE_BASE,
      agentKey: 'the-agent-key',
      agentId: AGENT_ID,
      agentSecret: AGENT_SECRET,
      fetch: fetchMock as unknown as typeof fetch,
    });
    expect(result).toBeNull();
  });

  it('createValidationCache returns stored values and clears', () => {
    const cache = createValidationCache();
    expect(cache.get('k')).toBeNull();
    const entry = {
      userId: 'u1',
      agentId: AGENT_ID,
      plan: 'basic',
      roles: ['user'],
      quotaRemaining: 1,
      subscriptionActive: true,
      billingModelType: null,
      measurementType: null,
      unitLabel: null,
    };
    cache.set('k', entry);
    expect(cache.get('k')).toEqual(entry);
    cache.clear();
    expect(cache.get('k')).toBeNull();
  });
});
