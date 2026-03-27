import { verifySignature, getUserContext } from './verifier';
import { calculateHmac } from './hmac';

describe('verifier', () => {
  const secret = 'my-secret';

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

  it('getUserContext parses headers', () => {
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
