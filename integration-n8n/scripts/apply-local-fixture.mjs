/**
 * Apply N8nIntegrationFixture JSON to example-workflows → example-workflows/local/.
 *
 * Fixture is written by agent-hub :e2e-tests-java:n8nIntegrationSetup (default:
 * agent-hub/e2e-tests-java/build/n8n-integration/local-fixture.json).
 *
 * Usage:
 *   node scripts/apply-local-fixture.mjs [--fixture path] [--out dir]
 *   npm run apply:local-fixture
 */
import { readFileSync, writeFileSync, mkdirSync, readdirSync, copyFileSync, existsSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const INTEGRATION_ROOT = join(__dirname, '..');
const DEFAULT_SOURCE_DIR = join(INTEGRATION_ROOT, 'example-workflows');
const DEFAULT_OUT_DIR = join(DEFAULT_SOURCE_DIR, 'local');

const GATEWAY_NODE_TYPES = new Set([
  'n8n-nodes-tollara.tollaraInvoke',
  'n8n-nodes-tollara.tollaraJobStatus',
  'n8n-nodes-tollara.tollaraJobResult',
]);

const CORE_NODE_TYPES = new Set([
  'n8n-nodes-tollara.tollaraValidateKey',
  'n8n-nodes-tollara.tollaraEstimateUsage',
]);

const USAGE_NODE_TYPES = new Set([
  'n8n-nodes-tollara.tollaraProgress',
  'n8n-nodes-tollara.tollaraComplete',
  'n8n-nodes-tollara.tollaraReportUsage',
]);

const WORKFLOW_REPLACEMENTS = {
  'backend-url-metadata-sync.json': (f) => ({
    YOUR_SERVICE_SECRET: f.serviceSecret,
  }),
  'backend-topic-brief-async.json': (f) => ({
    YOUR_SERVICE_SECRET: f.serviceSecret,
  }),
  'backend-echo-non-proxied.json': (f) => ({
    YOUR_SERVICE_SECRET: f.serviceSecret,
  }),
  'subscriber-proxied-sync-agent.json': (f) => ({
    YOUR_SERVICE_KEY: f.proxiedAgent.serviceKey,
    YOUR_SERVICE_ID: f.proxiedAgent.serviceId,
    YOUR_PROXIED_SYNC_ENDPOINT_ID: f.proxiedAgent.syncEndpointId,
  }),
  'subscriber-proxied-sync-agent-estimate.json': (f) => ({
    YOUR_SERVICE_KEY: f.proxiedAgent.serviceKey,
    YOUR_SERVICE_ID: f.proxiedAgent.serviceId,
    YOUR_SERVICE_SECRET: f.serviceSecret,
    YOUR_PROXIED_SYNC_ENDPOINT_ID: f.proxiedAgent.syncEndpointId,
  }),
  'subscriber-proxied-async-agent.json': (f) => ({
    YOUR_SERVICE_KEY: f.proxiedAgent.serviceKey,
    YOUR_SERVICE_ID: f.proxiedAgent.serviceId,
    YOUR_PROXIED_ASYNC_ENDPOINT_ID: f.proxiedAgent.asyncEndpointId,
  }),
  'subscriber-non-proxied-sync-agent.json': (f) => ({
    YOUR_SERVICE_KEY: f.nonProxiedAgent.serviceKey,
  }),
};

function defaultFixturePath() {
  const envPath = process.env.N8N_LOCAL_FIXTURE_PATH?.trim();
  if (envPath) {
    return resolve(envPath);
  }
  const sibling = resolve(INTEGRATION_ROOT, '../../agent-hub/e2e-tests-java/build/n8n-integration/local-fixture.json');
  if (existsSync(sibling)) {
    return sibling;
  }
  return resolve(INTEGRATION_ROOT, 'local-fixture.json');
}

function parseArgs(argv) {
  let fixturePath = defaultFixturePath();
  let outDir = DEFAULT_OUT_DIR;
  for (let i = 2; i < argv.length; i++) {
    if (argv[i] === '--fixture' && argv[i + 1]) {
      fixturePath = resolve(argv[++i]);
    } else if (argv[i] === '--out' && argv[i + 1]) {
      outDir = resolve(argv[++i]);
    } else if (argv[i] === '--help' || argv[i] === '-h') {
      console.log(`Usage: node scripts/apply-local-fixture.mjs [--fixture path] [--out dir]

  --fixture  Path to local-fixture.json (default: N8N_LOCAL_FIXTURE_PATH, sibling agent-hub path, or ./local-fixture.json)
  --out        Output directory (default: example-workflows/local)
`);
      process.exit(0);
    }
  }
  return { fixturePath, outDir };
}

function replaceInString(text, replacements) {
  let out = text;
  for (const [placeholder, value] of Object.entries(replacements)) {
    if (value != null) {
      out = out.split(placeholder).join(value);
    }
  }
  return out;
}

function patchTollaraNode(node, apiOrigins) {
  if (!node?.type?.startsWith('n8n-nodes-tollara.')) {
    return;
  }
  const params = node.parameters ?? {};
  if (GATEWAY_NODE_TYPES.has(node.type)) {
    params.setApiEndpoints = true;
    params.gatewayApiUrl = apiOrigins.gateway;
  } else if (CORE_NODE_TYPES.has(node.type)) {
    params.setApiEndpoints = true;
    params.coreApiUrl = apiOrigins.core;
  } else if (USAGE_NODE_TYPES.has(node.type)) {
    params.setApiEndpoints = true;
    params.usageApiUrl = apiOrigins.usage;
  }
  node.parameters = params;
}

function patchNonProxiedAgentUrl(jsCode, agentUrl) {
  if (!agentUrl) {
    return jsCode;
  }
  return jsCode.replace(
    /agentUrl:\s*'[^']*'/,
    `agentUrl: '${agentUrl.replace(/'/g, "\\'")}'`,
  );
}

/**
 * @param {object} workflow parsed workflow JSON
 * @param {string} filename basename e.g. subscriber-proxied-sync-agent.json
 * @param {object} fixture parsed fixture JSON
 */
export function applyFixtureToWorkflow(workflow, filename, fixture) {
  if (!fixture?.apiOrigins) {
    throw new Error('Fixture missing apiOrigins');
  }
  const replacementFn = WORKFLOW_REPLACEMENTS[filename];
  const replacements = replacementFn ? replacementFn(fixture) : {};

  for (const node of workflow.nodes ?? []) {
    patchTollaraNode(node, fixture.apiOrigins);

    if (node.parameters?.serviceSecret != null) {
      node.parameters.serviceSecret = replaceInString(
        String(node.parameters.serviceSecret),
        replacements,
      );
    }

    if (node.name === 'Set Config' && node.parameters?.jsCode) {
      let jsCode = replaceInString(node.parameters.jsCode, replacements);
      if (filename === 'subscriber-non-proxied-sync-agent.json') {
        jsCode = patchNonProxiedAgentUrl(jsCode, fixture.nonProxiedAgent?.agentUrl);
      }
      node.parameters.jsCode = jsCode;
    }
  }

  return workflow;
}

export function loadFixture(fixturePath) {
  const raw = readFileSync(fixturePath, 'utf8');
  const fixture = JSON.parse(raw);
  if (fixture.version !== 1) {
    throw new Error(`Unsupported fixture version: ${fixture.version}`);
  }
  return fixture;
}

function writeInvokeHelpers(outDir, fixture) {
  const backend = fixture.nonProxiedBackend;
  if (!backend?.serviceKey || !backend?.webhookUrl) {
    return [];
  }
  const lines = [
    '# Invoke backend-echo-non-proxied (n8n workflow must be active or use webhook-test URL)',
    `# Service: ${backend.serviceId ?? 'n8n Non-Proxied'}`,
    '',
    `curl -sS -X POST "${backend.webhookUrl}" \\`,
    `  -H "Authorization: Bearer ${backend.serviceKey}" \\`,
    '  -H "Content-Type: application/json" \\',
    `  -d "{\\"message\\":\\"Hello from curl\\"}"`,
    '',
  ];
  const path = join(outDir, 'invoke-backend-echo-non-proxied.sh');
  writeFileSync(path, `${lines.join('\n')}\n`, 'utf8');
  return [path];
}

export function applyFixture({ fixturePath, outDir, sourceDir = DEFAULT_SOURCE_DIR }) {
  const fixture = loadFixture(fixturePath);
  mkdirSync(outDir, { recursive: true });

  const workflowFiles = readdirSync(sourceDir).filter((f) => f.endsWith('.json'));
  const written = [];

  for (const filename of workflowFiles) {
    const sourcePath = join(sourceDir, filename);
    const workflow = JSON.parse(readFileSync(sourcePath, 'utf8'));
    applyFixtureToWorkflow(workflow, filename, fixture);
    const destPath = join(outDir, filename);
    writeFileSync(destPath, `${JSON.stringify(workflow, null, 2)}\n`, 'utf8');
    written.push(destPath);
  }

  const readmeSrc = join(sourceDir, 'README.md');
  if (existsSync(readmeSrc)) {
    copyFileSync(readmeSrc, join(outDir, 'README.md'));
  }

  const helpers = writeInvokeHelpers(outDir, fixture);
  written.push(...helpers);

  return { fixturePath, outDir, written, generatedAt: fixture.generatedAt };
}

function main() {
  const { fixturePath, outDir } = parseArgs(process.argv);
  if (!existsSync(fixturePath)) {
    console.error(`Fixture not found: ${fixturePath}`);
    console.error('Run agent-hub :e2e-tests-java:n8nIntegrationSetup -PrunE2eTests first, or pass --fixture path.');
    process.exit(1);
  }

  const result = applyFixture({ fixturePath, outDir });
  console.log(`Applied fixture (${result.generatedAt ?? 'unknown time'})`);
  console.log(`  fixture: ${result.fixturePath}`);
  console.log(`  output:  ${result.outDir}`);
  console.log(`  files:   ${result.written.length} workflow(s)`);
  console.log('');
  console.log('Import workflows from example-workflows/local/ in n8n, then run npm run repair:workflows if needed.');
}

if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
  main();
}
