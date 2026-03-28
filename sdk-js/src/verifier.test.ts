import { verifySignature, getUserContext, verifyInboundHmac, verifySignatureFromHeaders } from './verifier';
import { calculateHmac } from './hmac';

describe('verifier', () => {
  const secret = 'my-secret';

  it('verifyInboundHmac accepts same vector as verifySignature', () => {
    const payload = '';
    const timestamp = '1700000000';
    const userContextString = 'user1plan1role1,role210';
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
      },
    });
    expect(ok).toBe(true);
  });

  it('verifySignatureFromHeaders accepts lowercase keys', () => {
    const payload = '';
    const timestamp = '1700000000';
    const userContextString = 'user1plan1role1,role210';
    const dataToSign = payload + timestamp + userContextString;
    const signature = calculateHmac(dataToSign, secret);
    const ok = verifySignatureFromHeaders(
      secret,
      {
        'x-agentvend-signature': signature,
        'x-agentvend-timestamp': timestamp,
        'x-agentvend-user-id': 'user1',
        'x-agentvend-plan': 'plan1',
        'x-agentvend-roles': 'role1,role2',
        'x-agentvend-quota-remaining': '10',
      },
      payload
    );
    expect(ok).toBe(true);
  });

  it('verifySignature accepts valid HMAC', () => {
    const payload = '';
    const timestamp = '1700000000';
    const userContextString = 'user1plan1role1,role210';
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
    });
    expect(valid).toBe(true);
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
    });
    expect(valid).toBe(false);
  });

  it('getUserContext parses lowercase headers', () => {
    const ctx = getUserContext({
      'x-agentvend-user-id': 'u1',
      'x-agentvend-plan': 'p1',
      'x-agentvend-roles': 'r1,r2',
      'x-agentvend-quota-remaining': '5',
      'x-agentvend-subscription-active': 'true',
    });
    expect(ctx.userId).toBe('u1');
    expect(ctx.plan).toBe('p1');
    expect(ctx.roles).toEqual(['r1', 'r2']);
    expect(ctx.quotaRemaining).toBe(5);
    expect(ctx.subscriptionActive).toBe(true);
  });

  it('getUserContext parses canonical-case headers', () => {
    const ctx = getUserContext({
      'X-AgentVend-User-ID': 'u1',
      'X-AgentVend-Plan': 'p1',
      'X-AgentVend-Roles': 'r1,r2',
      'X-AgentVend-Quota-Remaining': '5',
      'X-AgentVend-Subscription-Active': 'true',
    });
    expect(ctx.userId).toBe('u1');
    expect(ctx.plan).toBe('p1');
    expect(ctx.roles).toEqual(['r1', 'r2']);
    expect(ctx.quotaRemaining).toBe(5);
    expect(ctx.subscriptionActive).toBe(true);
  });
});
