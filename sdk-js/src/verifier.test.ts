import { TollaraHeaders } from './tollaraHeaders';
import {
  verifySignature,
  getUserContext,
  verifyInboundHmac,
  verifySignatureFromHeaders,
  verifySignatureFromHeadersAndGetUserContext,
  buildGatewayUserContextString,
  buildGatewayUserContextStringV2,
} from './verifier';
import { calculateHmac } from './hmac';

describe('verifier', () => {
  const secret = 'my-secret';

  const extendedUcs = (subActive: boolean) =>
    buildGatewayUserContextString('user1', 'plan1', ['role1', 'role2'], 10, subActive, null, null, null);

  it('verifySignatureFromHeaders accepts gateway HMAC v2 when signing version is 2', () => {
    const payload = '';
    const timestamp = '1700000000';
    const ucs = buildGatewayUserContextStringV2('user1', 'plan1', ['role1', 'role2'], false, null, null, null);
    const signature = calculateHmac(payload + timestamp + ucs, secret);
    expect(
      verifySignatureFromHeaders(
        secret,
        {
          [TollaraHeaders.SIGNATURE.toLowerCase()]: signature,
          [TollaraHeaders.TIMESTAMP.toLowerCase()]: timestamp,
          [TollaraHeaders.SIGNING_VERSION.toLowerCase()]: '2',
          'x-tollara-user-id': 'user1',
          'x-tollara-plan': 'plan1',
          'x-tollara-roles': 'role1,role2',
          'x-tollara-subscription-active': 'false',
        },
        payload
      )
    ).toBe(true);
  });

  it('verifySignatureFromHeaders rejects v2 signature without signing version header', () => {
    const payload = '';
    const timestamp = '1700000000';
    const ucs = buildGatewayUserContextStringV2('user1', 'plan1', ['role1', 'role2'], false, null, null, null);
    const signature = calculateHmac(payload + timestamp + ucs, secret);
    expect(
      verifySignatureFromHeaders(
        secret,
        {
          'x-tollara-signature': signature,
          'x-tollara-timestamp': timestamp,
          'x-tollara-user-id': 'user1',
          'x-tollara-plan': 'plan1',
          'x-tollara-roles': 'role1,role2',
          'x-tollara-subscription-active': 'false',
        },
        payload
      )
    ).toBe(false);
  });

  it('verifyInboundHmac accepts extended canonical string', () => {
    const payload = '';
    const timestamp = '1700000000';
    const userContextString = extendedUcs(false);
    const dataToSign = payload + timestamp + userContextString;
    const signature = calculateHmac(dataToSign, secret);
    const ok = verifyInboundHmac(secret, {
      signature,
      timestamp,
      payload: '',
      signedUserContext: {
        userId: 'user1',
        plan: 'plan1',
        roles: ['role1', 'role2'],
        quotaRemaining: 10,
        subscriptionActive: false,
      },
    });
    expect(ok).toBe(true);
  });

  it('verifySignatureFromHeaders accepts lowercase keys', () => {
    const payload = '';
    const timestamp = '1700000000';
    const userContextString = extendedUcs(false);
    const dataToSign = payload + timestamp + userContextString;
    const signature = calculateHmac(dataToSign, secret);
    const ok = verifySignatureFromHeaders(
      secret,
      {
        'x-tollara-signature': signature,
        'x-tollara-timestamp': timestamp,
        'x-tollara-user-id': 'user1',
        'x-tollara-plan': 'plan1',
        'x-tollara-roles': 'role1,role2',
        'x-tollara-quota-remaining': '10',
        'x-tollara-subscription-active': 'false',
      },
      payload
    );
    expect(ok).toBe(true);
  });

  it('verifySignatureFromHeadersAndGetUserContext returns context when valid', () => {
    const payload = '';
    const timestamp = '1700000000';
    const userContextString = extendedUcs(false);
    const signature = calculateHmac(payload + timestamp + userContextString, secret);
    const ctx = verifySignatureFromHeadersAndGetUserContext(
      secret,
      {
        'x-tollara-signature': signature,
        'x-tollara-timestamp': timestamp,
        'x-tollara-user-id': 'user1',
        'x-tollara-plan': 'plan1',
        'x-tollara-roles': 'role1,role2',
        'x-tollara-quota-remaining': '10',
        'x-tollara-subscription-active': 'false',
      },
      payload
    );
    expect(ctx).not.toBeNull();
    expect(ctx!.userId).toBe('user1');
  });

  it('verifySignatureFromHeadersAndGetUserContext returns null when invalid', () => {
    const ctx = verifySignatureFromHeadersAndGetUserContext(
      secret,
      { 'x-tollara-signature': 'bad', 'x-tollara-timestamp': '1700000000' },
      ''
    );
    expect(ctx).toBeNull();
  });

  it('verifySignature accepts valid HMAC with extended context', () => {
    const payload = '';
    const timestamp = '1700000000';
    const userContextString = extendedUcs(false);
    const dataToSign = payload + timestamp + userContextString;
    const signature = calculateHmac(dataToSign, secret);

    const valid = verifySignature(secret, {
      signature,
      timestamp,
      payload: '',
      userId: 'user1',
      plan: 'plan1',
      roles: ['role1', 'role2'],
      quotaRemaining: 10,
      subscriptionActive: false,
    });
    expect(valid).toBe(true);
  });

  it('owner-like gateway vector matches platform', () => {
    const ownerSecret = 'test-agent-secret';
    const payload = '{"hello":1}';
    const ts = '1700000000';
    const ucs = buildGatewayUserContextString(
      'user-1',
      'owner',
      [],
      '9223372036854775807',
      true,
      null,
      null,
      null
    );
    const signature = calculateHmac(payload + ts + ucs, ownerSecret);
    expect(
      verifySignature(ownerSecret, {
        signature,
        timestamp: ts,
        payload,
        userId: 'user-1',
        plan: 'owner',
        roles: [],
        quotaRemaining: '9223372036854775807',
        subscriptionActive: true,
      })
    ).toBe(true);
  });

  it('subscriber vector with billing headers matches platform', () => {
    const ownerSecret = 'test-agent-secret';
    const payload = '';
    const ts = '1710000000';
    const ucs = buildGatewayUserContextString(
      'sub-user',
      'basic',
      ['roleA', 'roleB'],
      50,
      true,
      'SUBSCRIPTION',
      'PER_REQUEST',
      'request'
    );
    const signature = calculateHmac(payload + ts + ucs, ownerSecret);
    expect(
      verifySignature(ownerSecret, {
        signature,
        timestamp: ts,
        payload,
        userId: 'sub-user',
        plan: 'basic',
        roles: ['roleA', 'roleB'],
        quotaRemaining: 50,
        subscriptionActive: true,
        billingModelType: 'SUBSCRIPTION',
        measurementType: 'PER_REQUEST',
        unitLabel: 'request',
      })
    ).toBe(true);
  });

  it('verifySignature rejects wrong signature', () => {
    const valid = verifySignature(secret, {
      signature: 'wrong',
      timestamp: '1700000000',
      payload: '',
      userId: null,
      plan: null,
      roles: [],
      quotaRemaining: null,
      subscriptionActive: false,
    });
    expect(valid).toBe(false);
  });

  it('getUserContext parses lowercase headers', () => {
    const ctx = getUserContext({
      'x-tollara-user-id': 'u1',
      'x-tollara-plan': 'p1',
      'x-tollara-roles': 'r1,r2',
      'x-tollara-quota-remaining': '5',
      'x-tollara-subscription-active': 'true',
      'x-tollara-billing-model': 'SUBSCRIPTION',
      'x-tollara-measurement-type': 'PER_REQUEST',
      'x-tollara-unit-label': 'request',
    });
    expect(ctx.userId).toBe('u1');
    expect(ctx.plan).toBe('p1');
    expect(ctx.roles).toEqual(['r1', 'r2']);
    expect(ctx.quotaRemaining).toBe(5);
    expect(ctx.subscriptionActive).toBe(true);
    expect(ctx.billingModelType).toBe('SUBSCRIPTION');
    expect(ctx.measurementType).toBe('PER_REQUEST');
    expect(ctx.unitLabel).toBe('request');
  });

  it('getUserContext parses canonical-case headers', () => {
    const ctx = getUserContext({
      'X-Tollara-User-ID': 'u1',
      'X-Tollara-Plan': 'p1',
      'X-Tollara-Roles': 'r1,r2',
      'X-Tollara-Quota-Remaining': '5',
      'X-Tollara-Subscription-Active': 'true',
    });
    expect(ctx.userId).toBe('u1');
    expect(ctx.plan).toBe('p1');
    expect(ctx.roles).toEqual(['r1', 'r2']);
    expect(ctx.quotaRemaining).toBe(5);
    expect(ctx.subscriptionActive).toBe(true);
  });
});
