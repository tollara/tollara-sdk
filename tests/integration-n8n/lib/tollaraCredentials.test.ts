import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  getTollaraCredentials,
  optionalServiceId,
  requireCoreApiUrlWhenEndpointsEnabled,
  requireGatewayApiUrlWhenEndpointsEnabled,
  requireServiceId,
  requireServiceSecret,
  requireUsageApiUrlWhenEndpointsEnabled,
  resolveCoreApiUrl,
  resolveGatewayApiUrl,
  resolveUsageApiUrl,
  tollaraCredentialsFromNodeParameters,
} from '../../../integration-n8n/lib/tollaraCredentials';

describe('tollaraCredentials', () => {
  it('parses optional service URL overrides', () => {
    const creds = getTollaraCredentials({
      coreApiUrl: 'http://core:8081',
      usageApiUrl: 'http://usage:8084',
      gatewayApiUrl: '',
    });

    assert.equal(creds.coreApiUrl, 'http://core:8081');
    assert.equal(creds.usageApiUrl, 'http://usage:8084');
    assert.equal(creds.gatewayApiUrl, undefined);
  });

  it('resolveCoreApiUrl, resolveUsageApiUrl, and resolveGatewayApiUrl return service-specific URLs only', () => {
    const creds = getTollaraCredentials({
      coreApiUrl: 'http://core:8081',
      usageApiUrl: 'http://usage:8084',
      gatewayApiUrl: 'http://gateway:8083',
    });

    assert.equal(resolveCoreApiUrl(creds), 'http://core:8081');
    assert.equal(resolveUsageApiUrl(creds), 'http://usage:8084');
    assert.equal(resolveGatewayApiUrl(creds), 'http://gateway:8083');
  });

  it('resolve*ApiUrl returns undefined when service URL unset', () => {
    const creds = getTollaraCredentials({});

    assert.equal(resolveCoreApiUrl(creds), undefined);
    assert.equal(resolveUsageApiUrl(creds), undefined);
    assert.equal(resolveGatewayApiUrl(creds), undefined);
  });

  it('requireServiceSecret and requireServiceId require node values', () => {
    assert.equal(requireServiceSecret('node-secret'), 'node-secret');
    assert.throws(() => requireServiceSecret(''), /Service secret is required/);
    assert.throws(() => requireServiceSecret(undefined), /Service secret is required/);
    assert.equal(requireServiceId('uuid'), 'uuid');
    assert.throws(() => requireServiceId(''), /Service ID is required/);
  });

  it('optionalServiceId returns null when blank or import placeholder', () => {
    assert.equal(optionalServiceId(undefined), null);
    assert.equal(optionalServiceId(''), null);
    assert.equal(optionalServiceId('   '), null);
    assert.equal(optionalServiceId('YOUR_SERVICE_ID'), null);
    assert.equal(optionalServiceId('a1b2c3d4-e5f6-7890-abcd-ef1234567890'), 'a1b2c3d4-e5f6-7890-abcd-ef1234567890');
  });

  it('tollaraCredentialsFromNodeParameters returns empty overrides when toggle is off', () => {
    const executeFunctions = {
      getNodeParameter: (_name: string, _index: number, defaultValue?: unknown) => defaultValue ?? false,
      getNode: () => ({ parameters: { usageApiUrl: 'http://should-not-use' } }),
    };

    const creds = tollaraCredentialsFromNodeParameters(executeFunctions as never);
    assert.deepEqual(creds, {
      coreApiUrl: undefined,
      usageApiUrl: undefined,
      gatewayApiUrl: undefined,
    });
  });

  it('tollaraCredentialsFromNodeParameters reads node parameters when toggle is on', () => {
    const executeFunctions = {
      getNodeParameter: (name: string) => (name === 'setApiEndpoints' ? true : false),
      getNode: () => ({
        parameters: {
          usageApiUrl: 'http://usage:8084',
          gatewayApiUrl: 'http://gateway:8083',
        },
      }),
    };

    const creds = tollaraCredentialsFromNodeParameters(executeFunctions as never);
    assert.equal(creds.usageApiUrl, 'http://usage:8084');
    assert.equal(creds.gatewayApiUrl, 'http://gateway:8083');
    assert.equal(creds.coreApiUrl, undefined);
  });

  it('requireApiUrlWhenEndpointsEnabled throws when toggle on and URL missing', () => {
    const executeFunctions = {
      getNodeParameter: (name: string) => (name === 'setApiEndpoints' ? true : false),
      getNode: () => ({ parameters: {} }),
    };
    assert.throws(
      () => requireCoreApiUrlWhenEndpointsEnabled(executeFunctions as never),
      /Core API URL is required when Set API Endpoints is enabled/,
    );
    assert.throws(
      () => requireUsageApiUrlWhenEndpointsEnabled(executeFunctions as never),
      /Usage API URL is required when Set API Endpoints is enabled/,
    );
    assert.throws(
      () => requireGatewayApiUrlWhenEndpointsEnabled(executeFunctions as never),
      /Gateway API URL is required when Set API Endpoints is enabled/,
    );
  });

  it('requireApiUrlWhenEndpointsEnabled does not throw when toggle off', () => {
    const executeFunctions = {
      getNodeParameter: (_name: string, _index: number, defaultValue?: unknown) => defaultValue ?? false,
      getNode: () => ({ parameters: {} }),
    };
    requireCoreApiUrlWhenEndpointsEnabled(executeFunctions as never);
    requireUsageApiUrlWhenEndpointsEnabled(executeFunctions as never);
    requireGatewayApiUrlWhenEndpointsEnabled(executeFunctions as never);
  });

  it('requireApiUrlWhenEndpointsEnabled does not throw when toggle on and URL set', () => {
    const executeFunctions = {
      getNodeParameter: (name: string) => (name === 'setApiEndpoints' ? true : false),
      getNode: () => ({
        parameters: {
          coreApiUrl: 'http://core:8081',
          usageApiUrl: 'http://usage:8084',
          gatewayApiUrl: 'http://gateway:8083',
        },
      }),
    };
    requireCoreApiUrlWhenEndpointsEnabled(executeFunctions as never);
    requireUsageApiUrlWhenEndpointsEnabled(executeFunctions as never);
    requireGatewayApiUrlWhenEndpointsEnabled(executeFunctions as never);
  });
});
