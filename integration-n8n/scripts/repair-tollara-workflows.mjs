/**
 * Repair Tollara nodes in n8n workflow JSON saved in SQLite.
 * - Strips stale credentials.tollaraApi
 * - Restores default parameters when empty or missing required fields
 * - Copies serviceSecret onto Verify Request from sibling Tollara nodes
 * Usage: node scripts/repair-tollara-workflows.mjs <path-to-database.sqlite>
 */
import { execFileSync } from 'node:child_process';

const dbPath = process.argv[2];
if (!dbPath) {
  console.error('Usage: node repair-tollara-workflows.mjs <database.sqlite>');
  process.exit(1);
}

const NODE_TYPE_VERSIONS = {
  'n8n-nodes-tollara.tollaraVerifyRequest': 5,
  'n8n-nodes-tollara.tollaraValidateKey': 5,
};

const TOLLARA_DEFAULTS = {
  'n8n-nodes-tollara.tollaraVerifyRequest': {
    serviceSecret: '',
    rawBodyBinaryProperty: 'data',
  },
  'n8n-nodes-tollara.tollaraProgress': {
    serviceSecret: '',
    progressUrl: '',
    requestId: '',
    stage: '',
    percentageComplete: 0,
    errorMessage: '',
    setApiEndpoints: false,
  },
  'n8n-nodes-tollara.tollaraComplete': {
    serviceSecret: '',
    callbackUrl: '',
    requestId: '',
    status: 'COMPLETED',
    result: '',
    resultUrl: '',
    contentType: '',
    units: 0,
    setApiEndpoints: false,
  },
  'n8n-nodes-tollara.tollaraValidateKey': {
    serviceSecret: '',
    serviceKeySource: 'webhookAuthorization',
    serviceKey: '',
    serviceId: '',
    optionalServiceIdNotice: '',
    setApiEndpoints: false,
  },
  'n8n-nodes-tollara.tollaraInvoke': {
    httpMethod: 'POST',
    async: false,
    serviceKey: '',
    serviceId: '',
    endpointId: '',
    body: '',
    setApiEndpoints: false,
  },
  'n8n-nodes-tollara.tollaraJobStatus': {
    serviceKey: '',
    requestId: '',
    setApiEndpoints: false,
  },
  'n8n-nodes-tollara.tollaraJobResult': {
    serviceKey: '',
    requestId: '',
    setApiEndpoints: false,
  },
  'n8n-nodes-tollara.tollaraReportUsage': {
    serviceSecret: '',
    userId: '',
    serviceId: '',
    unitsUsed: 1,
    setApiEndpoints: false,
  },
  'n8n-nodes-tollara.tollaraEstimateUsage': {
    serviceSecret: '',
    serviceKey: '',
    serviceId: '',
    optionalServiceIdNotice: '',
    estimatedUnits: 1,
    setApiEndpoints: false,
  },
};

function runSqlite(args) {
  return execFileSync('sqlite3', args, { encoding: 'utf8' });
}

function repairNode(node) {
  if (!node?.type?.startsWith('n8n-nodes-tollara.')) {
    return false;
  }

  let changed = false;

  if (node.credentials?.tollaraApi) {
    delete node.credentials.tollaraApi;
    if (Object.keys(node.credentials).length === 0) {
      delete node.credentials;
    }
    changed = true;
  }

  const expectedTypeVersion = NODE_TYPE_VERSIONS[node.type] ?? 1;
  if (node.typeVersion !== expectedTypeVersion) {
    node.typeVersion = expectedTypeVersion;
    changed = true;
  }

  const defaults = TOLLARA_DEFAULTS[node.type];
  if (!defaults) {
    return changed;
  }

  const params = node.parameters ?? {};
  const isEmpty = Object.keys(params).length === 0;
  const missingRawBody =
    node.type === 'n8n-nodes-tollara.tollaraVerifyRequest' && params.rawBodyBinaryProperty == null;

  if (isEmpty || missingRawBody) {
    node.parameters = { ...defaults, ...params };
    changed = true;
  }

  if (node.parameters?.typeVersion != null) {
    delete node.parameters.typeVersion;
    changed = true;
  }

  return changed;
}

function repairWorkflowNodes(nodes) {
  let changed = false;

  const filtered = nodes.filter((node) => {
    if (
      node?.disabled === true &&
      node?.name === 'Import anchor (disabled, delete after import)' &&
      node?.type === 'n8n-nodes-tollara.tollaraProgress'
    ) {
      changed = true;
      return false;
    }
    return true;
  });

  if (filtered.length !== nodes.length) {
    nodes.length = 0;
    nodes.push(...filtered);
  }

  const siblingSecret = nodes
    .filter((n) => n?.type?.startsWith('n8n-nodes-tollara.') && n.type !== 'n8n-nodes-tollara.tollaraVerifyRequest')
    .map((n) => n.parameters?.serviceSecret)
    .find((secret) => typeof secret === 'string' && secret.trim().length > 0);

  for (const node of nodes) {
    if (node?.type === 'n8n-nodes-tollara.tollaraVerifyRequest' && siblingSecret) {
      const params = node.parameters ?? {};
      if (!params.serviceSecret?.trim()) {
        node.parameters = { ...params, serviceSecret: siblingSecret };
        changed = true;
      }
    }

    if (repairNode(node)) {
      changed = true;
    }
  }

  return changed;
}

const rowsJson = runSqlite(['-json', dbPath, 'SELECT id, name, nodes FROM workflow_entity;']);
const rows = JSON.parse(rowsJson || '[]');
let workflowsUpdated = 0;
let nodesUpdated = 0;

for (const row of rows) {
  let nodes;
  try {
    nodes = JSON.parse(row.nodes);
  } catch {
    continue;
  }

  if (!Array.isArray(nodes)) {
    continue;
  }

  if (repairWorkflowNodes(nodes)) {
    const escaped = JSON.stringify(nodes).replace(/'/g, "''");
    runSqlite([dbPath, `UPDATE workflow_entity SET nodes = '${escaped}', updatedAt = datetime('now') WHERE id = '${row.id.replace(/'/g, "''")}';`]);
    workflowsUpdated += 1;
    nodesUpdated += nodes.filter((n) => n?.type?.startsWith('n8n-nodes-tollara.')).length;
    console.log(`  repaired workflow "${row.name}" (${row.id})`);
  }
}

console.log(`Done: ${nodesUpdated} node(s) in ${workflowsUpdated} workflow(s).`);
