import {
  isHostedTollaraApiOrigin,
  resolveCorePathPrefix,
  resolveGatewayPathPrefix,
  resolveUsagePathPrefix,
} from './pathPrefixes';
import {
  DEFAULT_CORE_PATH_PREFIX,
  DEFAULT_GATEWAY_PATH_PREFIX,
  DEFAULT_USAGE_PATH_PREFIX,
  ECS_CORE_PATH_PREFIX,
  ECS_GATEWAY_PATH_PREFIX,
  ECS_USAGE_PATH_PREFIX,
} from './constants';

describe('pathPrefixes', () => {
  it('detects hosted Tollara API origins', () => {
    expect(isHostedTollaraApiOrigin('https://api.tollara.ai')).toBe(true);
    expect(isHostedTollaraApiOrigin('https://api.ppe.tollara.ai')).toBe(true);
    expect(isHostedTollaraApiOrigin('https://acme.api.tollara.ai')).toBe(true);
    expect(isHostedTollaraApiOrigin('http://host.docker.internal:8083')).toBe(false);
    expect(isHostedTollaraApiOrigin('http://localhost:8083')).toBe(false);
  });

  it('uses ECS gateway prefix for hosted prod origin', () => {
    expect(resolveGatewayPathPrefix('https://api.tollara.ai')).toBe(ECS_GATEWAY_PATH_PREFIX);
    expect(resolveGatewayPathPrefix(null)).toBe(ECS_GATEWAY_PATH_PREFIX);
    expect(resolveGatewayPathPrefix('http://host.docker.internal:8083')).toBe(DEFAULT_GATEWAY_PATH_PREFIX);
  });

  it('uses ECS core and usage prefixes for hosted prod origin', () => {
    expect(resolveCorePathPrefix('https://api.tollara.ai')).toBe(ECS_CORE_PATH_PREFIX);
    expect(resolveUsagePathPrefix('https://api.tollara.ai')).toBe(ECS_USAGE_PATH_PREFIX);
  });

  it('honours explicit prefix overrides', () => {
    expect(resolveGatewayPathPrefix('https://api.tollara.ai', '/api')).toBe('/api');
    expect(resolveCorePathPrefix('https://api.tollara.ai', '/api/v1')).toBe('/api/v1');
  });
});
