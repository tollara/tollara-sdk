/**
 * Verify the bind-mounted n8n-nodes-tollara package loads like n8n does at startup.
 * Run inside the n8n container (see deploy-local.ps1).
 */
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);

const packageRoot = '/home/node/.n8n/nodes/node_modules/n8n-nodes-tollara';
const nodesDir = '/home/node/.n8n/nodes/node_modules';
const n8nCore = '/usr/local/lib/node_modules/n8n/node_modules/n8n-core/dist/nodes-loader';

try {
  require(`${packageRoot}/index.js`);
} catch (error) {
  console.error(`VERIFY_FAIL missing or invalid package entry (index.js): ${error.message}`);
  process.exit(1);
}

const { scanDirectoryForPackages } = require(`${n8nCore}/scan-directory-for-packages.js`);

const loaders = await scanDirectoryForPackages(nodesDir, {});
const loader = loaders.find((entry) => entry.packageName === 'n8n-nodes-tollara');

if (!loader) {
  console.error('VERIFY_FAIL n8n-nodes-tollara not found under ~/.n8n/nodes/node_modules');
  process.exit(1);
}

try {
  await loader.loadAll();
} catch (error) {
  console.error(`VERIFY_FAIL loadAll(): ${error.message}`);
  process.exit(1);
}

const expected = [
  'tollaraVerifyRequest',
  'tollaraInvoke',
  'tollaraJobStatus',
  'tollaraJobResult',
  'tollaraProgress',
  'tollaraComplete',
  'tollaraValidateKey',
  'tollaraReportUsage',
  'tollaraEstimateUsage',
];

const missing = expected.filter((name) => !(name in loader.known.nodes));
if (missing.length > 0) {
  console.error(`VERIFY_FAIL missing nodes after loadAll(): ${missing.join(', ')}`);
  process.exit(1);
}

console.log(`VERIFY_OK n8n-nodes-tollara (${expected.length} nodes)`);
