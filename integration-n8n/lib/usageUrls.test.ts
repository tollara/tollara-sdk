import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { rewriteUsageServiceUrl } from './usageUrls';

describe('rewriteUsageServiceUrl', () => {
  it('rewrites prod host to local usage service while keeping path and query', () => {
    const rewritten = rewriteUsageServiceUrl(
      'https://tollara.ai/api/usage/progress/req-1?signature=abc&timestamp=123',
      'http://host.docker.internal:8084',
    );
    assert.equal(
      rewritten,
      'http://host.docker.internal:8084/api/usage/progress/req-1?signature=abc&timestamp=123',
    );
  });

  it('returns original URL when usage override is unset', () => {
    const url = 'https://tollara.ai/api/usage/complete/req-1?timestamp=1';
    assert.equal(rewriteUsageServiceUrl(url, undefined), url);
  });
});
