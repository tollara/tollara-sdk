import { execFileSync } from 'node:child_process';

const workflowId = process.argv[2] ?? 'ayPpEOHZoXfUDyMK';
const nodeName = process.argv[3] ?? 'Tollara Verify Request';
const dbPath = process.argv[4] ?? '/n8n/database.sqlite';

const rowsJson = execFileSync('sqlite3', ['-json', dbPath, `SELECT id, name, nodes FROM workflow_entity WHERE id = '${workflowId}';`], {
  encoding: 'utf8',
});
const rows = JSON.parse(rowsJson || '[]');
if (rows.length === 0) {
  console.error('Workflow not found:', workflowId);
  process.exit(1);
}

const nodes = JSON.parse(rows[0].nodes);
const node = nodes.find((n) => n.name === nodeName);
console.log('workflow:', rows[0].name, rows[0].id);
console.log(JSON.stringify(node, null, 2));
