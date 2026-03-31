import { createHmac, timingSafeEqual } from 'crypto';

/**
 * HMAC-SHA256 with UTF-8 key/message and Base64 output (per docs/hmac-spec.md).
 */
export function calculateHmac(data: string, key: string): string {
  const hmac = createHmac('sha256', key);
  hmac.update(data, 'utf8');
  return hmac.digest('base64');
}

/**
 * Outbound signing: canonical string = bodyString + timestamp.
 */
export function calculateHmacWithTimestamp(bodyString: string, timestamp: string | number, key: string): string {
  const ts = typeof timestamp === 'number' ? String(timestamp) : timestamp;
  return calculateHmac(bodyString + ts, key);
}

/**
 * Constant-time string comparison to avoid timing attacks.
 */
export function constantTimeEquals(a: string, b: string): boolean {
  if (a == null || b == null) return a === b;
  if (a.length !== b.length) return false;
  try {
    const bufA = Buffer.from(a, 'utf8');
    const bufB = Buffer.from(b, 'utf8');
    if (bufA.length !== bufB.length) return false;
    return timingSafeEqual(bufA, bufB);
  } catch {
    return false;
  }
}

/**
 * Validate that signature matches HMAC(payloadString, key).
 */
export function validateHmacSignature(signature: string, payloadString: string, key: string): boolean {
  if (!signature || !key) return false;
  try {
    const expected = calculateHmac(payloadString, key);
    return constantTimeEquals(expected, signature);
  } catch {
    return false;
  }
}
