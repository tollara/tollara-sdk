import { calculateHmac, calculateHmacWithTimestamp, constantTimeEquals, validateHmacSignature } from './hmac';

describe('hmac', () => {
  it('matches outbound test vector from docs/hmac-spec.md', () => {
    const data = '1234567890';
    const key = 'secret';
    expect(calculateHmac(data, key)).toBe('Bgs+chJF8gBA3xW2542Tm7B7l571zTPfLMBiCBwOp2c=');
  });

  it('calculateHmacWithTimestamp uses body + timestamp', () => {
    const sig = calculateHmacWithTimestamp('{}', '1700000000', 'k');
    expect(sig).toBeTruthy();
    expect(sig.length).toBeGreaterThan(0);
  });

  it('validateHmacSignature returns true for valid signature', () => {
    const data = '1234567890';
    const key = 'secret';
    const sig = calculateHmac(data, key);
    expect(validateHmacSignature(sig, data, key)).toBe(true);
  });

  it('validateHmacSignature returns false for invalid signature', () => {
    expect(validateHmacSignature('wrong', '1234567890', 'secret')).toBe(false);
  });

  it('constantTimeEquals', () => {
    expect(constantTimeEquals('a', 'a')).toBe(true);
    expect(constantTimeEquals('a', 'b')).toBe(false);
    expect(constantTimeEquals('ab', 'a')).toBe(false);
  });
});
