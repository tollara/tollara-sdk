import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  getTollaraCredentials,
  resolveCoreApiUrl,
  resolveGatewayApiUrl,
  resolveServiceApiUrl,
  resolveUsageApiUrl,
} from './tollaraCredentials';

describe('tollaraCredentials', () => {
  it('parses optional service URL overrides', () => {
    const creds = getTollaraCredentials({
      serviceSecret: 'secret',
      apiUrl: ' https://api.example.com ',
      coreApiUrl: 'http://core:8081',
      usageApiUrl: 'http://usage:8084',
      gatewayApiUrl: '',
    });

    assert.equal(creds.apiUrl, 'https://api.example.com');
    assert.equal(creds.coreApiUrl, 'http://core:8081');
    assert.equal(creds.usageApiUrl, 'http://usage:8084');
    assert.equal(creds.gatewayApiUrl, undefined);
  });

  it('resolveServiceApiUrl prefers service override', () => {
    assert.equal(resolveServiceApiUrl('http://usage:8084', 'https://api.example.com'), 'http://usage:8084');
    assert.equal(resolveServiceApiUrl(undefined, 'https://api.example.com'), 'https://api.example.com');
    assert.equal(resolveServiceApiUrl(undefined, undefined), undefined);
  });

  it('resolveCoreApiUrl and resolveUsageApiUrl use independent overrides', () => {
    const creds = getTollaraCredentials({
      serviceSecret: 'secret',
      coreApiUrl: 'http://core:8081',
      usageApiUrl: 'http://usage:8084',
    });

    assert.equal(resolveCoreApiUrl(creds), 'http://core:8081');
    assert.equal(resolveUsageApiUrl(creds), 'http://usage:8084');
    assert.equal(resolveGatewayApiUrl(creds), undefined);
  });

  it('resolveGatewayApiUrl falls back to shared apiUrl', () => {
    const creds = getTollaraCredentials({
      serviceSecret: 'secret',
      apiUrl: 'https://api.example.com',
    });

    assert.equal(resolveGatewayApiUrl(creds), 'https://api.example.com');
  });
});
