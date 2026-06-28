/**
 * Remove stale credentials.tollaraApi from all workflow nodes in n8n's SQLite database.
 * Usage: node scripts/strip-stale-tollara-credentials.mjs <path-to-database.sqlite>
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const dbPath = process.argv[2];
if (!dbPath) {
  console.error('Usage: node strip-stale-tollara-credentials.mjs <database.sqlite>');
  process.exit(1);
}

function runSqlite(args) {
  return execFileSync('sqlite3', args, { encoding: 'utf8' });
}

function tableExists() {
  const result = runSqlite([dbPath, "SELECT name FROM sqlite_master WHERE type='table' AND name='workflow_entity';"]);
  return result.trim().length > 0;
}

if (!tableExists()) {
  console.log('No workflow_entity table — skipping.');
  process.exit(0);
}

const rowsJson = runSqlite([dbPath, '-json', 'SELECT id, name, nodes FROM workflow_entity;']);
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

  let changed = false;
  for (const node of nodes) {
    if (node?.credentials?.tollaraApi) {
      delete node.credentials.tollaraApi;
      if (Object.keys(node.credentials).length === 0) {
        delete node.credentials;
      }
      nodesUpdated += 1;
      changed = true;
    }
  }

  if (changed) {
    const escaped = JSON.stringify(nodes).replace(/'/g, "''");
    runSqlite([dbPath, `UPDATE workflow_entity SET nodes = '${escaped}', updatedAt = datetime('now') WHERE id = '${row.id.replace(/'/g, "''")}';`]);
    workflowsUpdated += 1;
    console.log(`  stripped tollaraApi from workflow "${row.name}" (${row.id})`);
  }
}

console.log(`Done: ${nodesUpdated} node(s) in ${workflowsUpdated} workflow(s).`);
