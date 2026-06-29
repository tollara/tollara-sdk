import { TollaraHeaders } from './tollaraHeaders';
import { calculateHmac } from './hmac';
import { createValidationCache, estimateUsage, validateServiceKey, validateServiceKeyWithOutcome } from './validationClient';

const CORE_BASE = 'http://core.test';
const SERVICE_ID = '550e8400-e29b-41d4-a716-446655440000';
const SERVICE_KEY_ID = '6ba7b810-9dad-11d1-80b4-00c04fd430c8';
const SERVICE_SECRET = 'test-service-secret';

describe('validationClient', () => {
  it('returns parsed v3 validation result for signed valid response', async () => {
    const responseBody = JSON.stringify({
      valid: true,
      serviceKeyId: SERVICE_KEY_ID,
      userId: 'user-123',
      serviceId: SERVICE_ID,
      serviceProductId: 'prod-uuid-1',
      roles: ['user'],
      subscriptionStatus: 'ACTIVE',
      billingModelType: 'SUBSCRIPTION',
      measurementType: 'PER_REQUEST',
      unitLabel: 'request',
      timestamp: 1700000000,
      error: null,
      validationSchemaVersion: 3,
    });
    const timestamp = '1700000000';
    const signature = calculateHmac(responseBody + timestamp, SERVICE_SECRET);

    const fetchMock: typeof fetch = async () =>
      new Response(responseBody, {
        status: 200,
        headers: {
          'Content-Type': 'application/json',
          [TollaraHeaders.SIGNATURE]: signature,
          [TollaraHeaders.TIMESTAMP]: timestamp,
        },
      });

    const result = await validateServiceKey({
      baseUrl: CORE_BASE,
      serviceKey: 'bearer-token',
      serviceId: SERVICE_ID,
      serviceSecret: SERVICE_SECRET,
      fetch: fetchMock,
    });

    expect(result).toEqual({
      userId: 'user-123',
      serviceId: SERVICE_ID,
      serviceKeyId: SERVICE_KEY_ID,
      serviceProductId: 'prod-uuid-1',
      roles: ['user'],
      subscriptionStatus: 'ACTIVE',
      validationSchemaVersion: 3,
      billingModelType: 'SUBSCRIPTION',
      measurementType: 'PER_REQUEST',
      unitLabel: 'request',
      grantAccess: true,
    });
  });

  it('returns null when service key is blank', async () => {
    const fetchMock = jest.fn();
    const result = await validateServiceKey({
      baseUrl: CORE_BASE,
      serviceKey: '   ',
      serviceId: SERVICE_ID,
      serviceSecret: SERVICE_SECRET,
      fetch: fetchMock as unknown as typeof fetch,
    });
    expect(result).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('returns null when response is not ok', async () => {
    const fetchMock: typeof fetch = async () => new Response('unauthorized', { status: 401 });
    const result = await validateServiceKey({
      baseUrl: CORE_BASE,
      serviceKey: 'bad',
      serviceId: SERVICE_ID,
      serviceSecret: SERVICE_SECRET,
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
          [TollaraHeaders.SIGNATURE]: 'bad-signature',
          [TollaraHeaders.TIMESTAMP]: '1700000000',
        },
      });
    const result = await validateServiceKey({
      baseUrl: CORE_BASE,
      serviceKey: 'k',
      serviceId: SERVICE_ID,
      serviceSecret: SERVICE_SECRET,
      fetch: fetchMock,
    });
    expect(result).toBeNull();
  });

  it('posts JSON body to /service-keys/validate', async () => {
    const responseBody = JSON.stringify({ valid: false });
    const timestamp = '1700000000';
    const signature = calculateHmac(responseBody + timestamp, SERVICE_SECRET);
    const fetchMock = jest.fn(async (input: string | URL | Request, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
      expect(url).toBe(`${CORE_BASE}/api/v1/service-keys/validate`);
      expect(init?.method).toBe('POST');
      expect(init?.headers).toEqual({ 'Content-Type': 'application/json' });
      expect(JSON.parse(String(init?.body))).toEqual({
        serviceKey: 'the-service-key',
        serviceId: SERVICE_ID,
        serviceSecret: SERVICE_SECRET,
      });
      return new Response(responseBody, {
        status: 200,
        headers: {
          [TollaraHeaders.SIGNATURE]: signature,
          [TollaraHeaders.TIMESTAMP]: timestamp,
        },
      });
    });

    const result = await validateServiceKey({
      baseUrl: CORE_BASE,
      serviceKey: 'the-service-key',
      serviceId: SERVICE_ID,
      serviceSecret: SERVICE_SECRET,
      fetch: fetchMock as unknown as typeof fetch,
    });
    expect(result).toBeNull();
  });

  describe('validateServiceKeyWithOutcome', () => {
    it('returns ok:true for signed valid response', async () => {
      const responseBody = JSON.stringify({
        valid: true,
        userId: 'user-123',
        serviceId: SERVICE_ID,
        subscriptionStatus: 'ACTIVE',
        validationSchemaVersion: 3,
      });
      const timestamp = '1700000000';
      const signature = calculateHmac(responseBody + timestamp, SERVICE_SECRET);
      const fetchMock: typeof fetch = async () =>
        new Response(responseBody, {
          status: 200,
          headers: {
            [TollaraHeaders.SIGNATURE]: signature,
            [TollaraHeaders.TIMESTAMP]: timestamp,
          },
        });

      const outcome = await validateServiceKeyWithOutcome({
        baseUrl: CORE_BASE,
        serviceKey: 'key',
        serviceId: SERVICE_ID,
        serviceSecret: SERVICE_SECRET,
        fetch: fetchMock,
      });

      expect(outcome.ok).toBe(true);
      if (outcome.ok) {
        expect(outcome.result.grantAccess).toBe(true);
      }
    });

    it('returns MISSING_KEY without HTTP call', async () => {
      const fetchMock = jest.fn();
      const outcome = await validateServiceKeyWithOutcome({
        baseUrl: CORE_BASE,
        serviceKey: '  ',
        serviceId: SERVICE_ID,
        serviceSecret: SERVICE_SECRET,
        fetch: fetchMock as unknown as typeof fetch,
      });
      expect(outcome).toEqual({ ok: false, code: 'MISSING_KEY' });
      expect(fetchMock).not.toHaveBeenCalled();
    });

    it('returns HTTP_ERROR for unsigned 401', async () => {
      const fetchMock: typeof fetch = async () => new Response('unauthorized', { status: 401 });
      const outcome = await validateServiceKeyWithOutcome({
        baseUrl: CORE_BASE,
        serviceKey: 'bad',
        serviceId: SERVICE_ID,
        serviceSecret: SERVICE_SECRET,
        fetch: fetchMock,
      });
      expect(outcome).toEqual({ ok: false, code: 'HTTP_ERROR', httpStatus: 401 });
    });

    it('returns HMAC_MISMATCH for bad signature', async () => {
      const responseBody = JSON.stringify({ valid: true, userId: 'u1' });
      const fetchMock: typeof fetch = async () =>
        new Response(responseBody, {
          status: 200,
          headers: {
            [TollaraHeaders.SIGNATURE]: 'bad-signature',
            [TollaraHeaders.TIMESTAMP]: '1700000000',
          },
        });
      const outcome = await validateServiceKeyWithOutcome({
        baseUrl: CORE_BASE,
        serviceKey: 'k',
        serviceId: SERVICE_ID,
        serviceSecret: SERVICE_SECRET,
        fetch: fetchMock,
      });
      expect(outcome).toEqual({ ok: false, code: 'HMAC_MISMATCH', httpStatus: 200 });
    });

    it('returns INVALID_KEY with message from signed valid:false body', async () => {
      const responseBody = JSON.stringify({ valid: false, error: 'Key expired' });
      const timestamp = '1700000000';
      const signature = calculateHmac(responseBody + timestamp, SERVICE_SECRET);
      const fetchMock: typeof fetch = async () =>
        new Response(responseBody, {
          status: 200,
          headers: {
            [TollaraHeaders.SIGNATURE]: signature,
            [TollaraHeaders.TIMESTAMP]: timestamp,
          },
        });
      const outcome = await validateServiceKeyWithOutcome({
        baseUrl: CORE_BASE,
        serviceKey: 'expired',
        serviceId: SERVICE_ID,
        serviceSecret: SERVICE_SECRET,
        fetch: fetchMock,
      });
      expect(outcome).toEqual({
        ok: false,
        code: 'INVALID_KEY',
        message: 'Key expired',
        httpStatus: 200,
      });
    });

    it('returns NETWORK on fetch failure', async () => {
      const fetchMock: typeof fetch = async () => {
        throw new Error('connection refused');
      };
      const outcome = await validateServiceKeyWithOutcome({
        baseUrl: CORE_BASE,
        serviceKey: 'k',
        serviceId: SERVICE_ID,
        serviceSecret: SERVICE_SECRET,
        fetch: fetchMock,
      });
      expect(outcome).toEqual({ ok: false, code: 'NETWORK' });
    });

    it('returns ok:true with grantAccess false for EXPIRED subscription', async () => {
      const responseBody = JSON.stringify({
        valid: true,
        userId: 'user-123',
        serviceId: SERVICE_ID,
        subscriptionStatus: 'EXPIRED',
        validationSchemaVersion: 3,
      });
      const timestamp = '1700000000';
      const signature = calculateHmac(responseBody + timestamp, SERVICE_SECRET);
      const fetchMock: typeof fetch = async () =>
        new Response(responseBody, {
          status: 200,
          headers: {
            [TollaraHeaders.SIGNATURE]: signature,
            [TollaraHeaders.TIMESTAMP]: timestamp,
          },
        });
      const outcome = await validateServiceKeyWithOutcome({
        baseUrl: CORE_BASE,
        serviceKey: 'key',
        serviceId: SERVICE_ID,
        serviceSecret: SERVICE_SECRET,
        fetch: fetchMock,
      });
      expect(outcome.ok).toBe(true);
      if (outcome.ok) {
        expect(outcome.result.grantAccess).toBe(false);
        expect(outcome.result.subscriptionStatus).toBe('EXPIRED');
      }
    });
  });

  it('estimateUsage returns parsed v3 result for signed 200 response', async () => {
    const responseBody = JSON.stringify({
      sufficientCredits: true,
      wouldExceedCap: false,
      wouldAllow: true,
      estimatedCost: 0.1,
      billingModelType: 'SUBSCRIPTION',
      measurementType: 'PER_REQUEST',
      unitLabel: 'request',
      breakdown: {
        unitsUsed: 1,
        unitsRemaining: 199,
        remainingSpendingCap: 20,
        isOverLimit: false,
      },
      estimateSchemaVersion: 3,
      timestamp: 1700000000,
    });
    const timestamp = '1700000000';
    const signature = calculateHmac(responseBody + timestamp, SERVICE_SECRET);

    const fetchMock: typeof fetch = async (input) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
      expect(url).toBe(`${CORE_BASE}/api/v1/service-keys/estimate-usage`);
      return new Response(responseBody, {
        status: 200,
        headers: {
          'Content-Type': 'application/json',
          [TollaraHeaders.SIGNATURE]: signature,
          [TollaraHeaders.TIMESTAMP]: timestamp,
        },
      });
    };

    const result = await estimateUsage({
      baseUrl: CORE_BASE,
      serviceKey: 'key-1',
      serviceId: SERVICE_ID,
      serviceSecret: SERVICE_SECRET,
      estimatedUnits: 1.5,
      fetch: fetchMock,
    });

    expect(result).not.toBeNull();
    expect(result!.httpStatus).toBe(200);
    expect(result!.wouldAllow).toBe(true);
    expect(result!.estimateSchemaVersion).toBe(3);
    expect(result!.breakdown?.unitsRemaining).toBe(199);
    expect(result!.breakdown?.remainingSpendingCap).toBe(20);
  });

  it('estimateUsage returns null when HMAC invalid', async () => {
    const responseBody = JSON.stringify({ wouldAllow: false, estimateSchemaVersion: 3, timestamp: 1700000000 });
    const fetchMock: typeof fetch = async () =>
      new Response(responseBody, {
        status: 200,
        headers: {
          [TollaraHeaders.SIGNATURE]: 'bad',
          [TollaraHeaders.TIMESTAMP]: '1700000000',
        },
      });
    const result = await estimateUsage({
      baseUrl: CORE_BASE,
      serviceKey: 'k',
      serviceId: SERVICE_ID,
      serviceSecret: SERVICE_SECRET,
      estimatedUnits: 1,
      fetch: fetchMock,
    });
    expect(result).toBeNull();
  });

  it('estimateUsage returns null when units not positive', async () => {
    const fetchMock = jest.fn();
    expect(
      await estimateUsage({
        baseUrl: CORE_BASE,
        serviceKey: 'k',
        serviceId: SERVICE_ID,
        serviceSecret: SERVICE_SECRET,
        estimatedUnits: 0,
        fetch: fetchMock as unknown as typeof fetch,
      })
    ).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('createValidationCache returns stored values and clears', () => {
    const cache = createValidationCache();
    expect(cache.get('k')).toBeNull();
    const entry = {
      userId: 'u1',
      serviceId: SERVICE_ID,
      serviceKeyId: null,
      serviceProductId: 'prod-1',
      roles: ['user'],
      subscriptionStatus: 'ACTIVE',
      validationSchemaVersion: 3,
      billingModelType: 'SUBSCRIPTION',
      measurementType: null,
      unitLabel: null,
      grantAccess: true,
    };
    cache.set('k', entry);
    expect(cache.get('k')).toEqual(entry);
    cache.clear();
    expect(cache.get('k')).toBeNull();
  });
});
