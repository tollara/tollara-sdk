import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { applyFixture, applyFixtureToWorkflow } from './apply-local-fixture.mjs';

const FIXTURE = {
  version: 1,
  serviceSecret: 'test-secret',
  apiOrigins: {
    core: 'http://host.docker.internal:8081',
    gateway: 'http://host.docker.internal:8083',
    usage: 'http://host.docker.internal:8084',
  },
  proxiedAgent: {
    serviceKey: 'pk-test',
    serviceId: 'svc-proxied',
    syncEndpointId: 'ep-sync',
    asyncEndpointId: 'ep-async',
  },
  nonProxiedAgent: {
    serviceKey: 'nk-test',
    agentUrl: 'http://host.docker.internal:9091/api/test/sync',
  },
};

test('subscriber-proxied-sync-agent replaces Set Config and enables gateway on Invoke', () => {
  const workflow = {
    nodes: [
      {
        name: 'Set Config',
        type: 'n8n-nodes-base.code',
        parameters: {
          jsCode: "return [{ json: { serviceKey: 'YOUR_SERVICE_KEY', serviceId: 'YOUR_SERVICE_ID', endpointId: 'YOUR_PROXIED_SYNC_ENDPOINT_ID' } }];",
        },
      },
      {
        name: 'Tollara Invoke',
        type: 'n8n-nodes-tollara.tollaraInvoke',
        parameters: { serviceKey: '' },
      },
    ],
  };

  applyFixtureToWorkflow(workflow, 'subscriber-proxied-sync-agent.json', FIXTURE);

  assert.match(workflow.nodes[0].parameters.jsCode, /pk-test/);
  assert.match(workflow.nodes[0].parameters.jsCode, /svc-proxied/);
  assert.match(workflow.nodes[0].parameters.jsCode, /ep-sync/);
  assert.equal(workflow.nodes[1].parameters.setApiEndpoints, true);
  assert.equal(workflow.nodes[1].parameters.gatewayApiUrl, FIXTURE.apiOrigins.gateway);
});

test('backend-echo-non-proxied sets core and usage API origins', () => {
  const workflow = {
    nodes: [
      {
        type: 'n8n-nodes-tollara.tollaraValidateKey',
        parameters: { serviceSecret: 'YOUR_SERVICE_SECRET' },
      },
      {
        type: 'n8n-nodes-tollara.tollaraReportUsage',
        parameters: { serviceSecret: 'YOUR_SERVICE_SECRET' },
      },
    ],
  };

  applyFixtureToWorkflow(workflow, 'backend-echo-non-proxied.json', FIXTURE);

  assert.equal(workflow.nodes[0].parameters.serviceSecret, 'test-secret');
  assert.equal(workflow.nodes[0].parameters.setApiEndpoints, true);
  assert.equal(workflow.nodes[0].parameters.coreApiUrl, FIXTURE.apiOrigins.core);
  assert.equal(workflow.nodes[1].parameters.setApiEndpoints, true);
  assert.equal(workflow.nodes[1].parameters.usageApiUrl, FIXTURE.apiOrigins.usage);
});

test('subscriber-non-proxied-sync-agent patches agentUrl in Set Config', () => {
  const workflow = {
    nodes: [
      {
        name: 'Set Config',
        type: 'n8n-nodes-base.code',
        parameters: {
          jsCode: "return [{ json: { serviceKey: 'YOUR_SERVICE_KEY', agentUrl: 'http://host.docker.internal:9091/api/test/sync' } }];",
        },
      },
    ],
  };

  applyFixtureToWorkflow(workflow, 'subscriber-non-proxied-sync-agent.json', FIXTURE);

  assert.match(workflow.nodes[0].parameters.jsCode, /nk-test/);
  assert.match(workflow.nodes[0].parameters.jsCode, /host\.docker\.internal:9091\/api\/test\/sync/);
});

test('applyFixture writes invoke script for non-proxied backend', () => {
  const dir = mkdtempSync(join(tmpdir(), 'n8n-fixture-'));
  try {
    const fixturePath = join(dir, 'fixture.json');
    writeFileSync(fixturePath, JSON.stringify({
      version: 1,
      serviceSecret: 'sec',
      apiOrigins: { core: 'http://c:8081', gateway: 'http://g:8083', usage: 'http://u:8084' },
      nonProxiedBackend: {
        serviceKey: 'agk_test_key',
        serviceId: 'svc-1',
        webhookUrl: 'http://localhost:5678/webhook-test/subscriber-echo',
      },
      proxiedAgent: { serviceKey: 'pk', serviceId: 's', syncEndpointId: 'e', asyncEndpointId: 'a' },
      nonProxiedAgent: { serviceKey: 'nk', agentUrl: 'http://host.docker.internal:9091/api/test/sync' },
    }));
    const outDir = join(dir, 'local');
    const sourceDir = join(dir, 'src');
    mkdirSync(sourceDir);
    writeFileSync(join(sourceDir, 'backend-echo-non-proxied.json'), '{"name":"x","nodes":[]}');

    const result = applyFixture({ fixturePath, outDir, sourceDir });
    const invokeScript = join(outDir, 'invoke-backend-echo-non-proxied.sh');
    assert.ok(result.written.includes(invokeScript));
    const content = readFileSync(invokeScript, 'utf8');
    assert.match(content, /agk_test_key/);
    assert.match(content, /subscriber-echo/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
