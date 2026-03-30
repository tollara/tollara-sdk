import { buildUsageReportUrl, DEFAULT_USAGE_PATH_PREFIX, reportUsage } from './usageClient';

describe('usageClient', () => {
  describe('buildUsageReportUrl', () => {
    it('uses default prefix /api/usage', () => {
      expect(buildUsageReportUrl('http://u.test')).toBe('http://u.test/api/usage/report');
      expect(buildUsageReportUrl('http://u.test/')).toBe('http://u.test/api/usage/report');
    });

    it('joins custom prefix without double slashes', () => {
      expect(buildUsageReportUrl('http://u.test', '/usage/api/v1')).toBe(
        'http://u.test/usage/api/v1/report'
      );
      expect(buildUsageReportUrl('http://u.test', 'usage/api/v1')).toBe(
        'http://u.test/usage/api/v1/report'
      );
    });

    it('exports default constant', () => {
      expect(DEFAULT_USAGE_PATH_PREFIX).toBe('/api/usage');
    });
  });

  it('reportUsage posts to custom usagePathPrefix', async () => {
    const calls: string[] = [];
    const mockFetch: typeof fetch = async (input) => {
      const url =
        typeof input === 'string' ? input : input instanceof URL ? input.href : (input as Request).url;
      calls.push(url);
      return new Response(
        JSON.stringify({
          status: 'ok',
          isOverLimit: false,
          remainingRequestsPerPeriod: 1,
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      );
    };

    await reportUsage({
      usageServiceUrl: 'http://u.test',
      userId: 'u1',
      agentId: 'a1',
      unitsUsed: 2,
      agentSecret: 's',
      usagePathPrefix: '/custom/prefix',
      fetch: mockFetch,
    });

    expect(calls).toEqual(['http://u.test/custom/prefix/report']);
  });
});
