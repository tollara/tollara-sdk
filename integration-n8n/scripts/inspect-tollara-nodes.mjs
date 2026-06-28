import { execFileSync } from 'node:child_process';

const workflowId = process.argv[2] ?? 'JR5dQX1bX5h5kJ6Q';
const dbPath = process.argv[3] ?? '/n8n/database.sqlite';

const rowsJson = execFileSync('sqlite3', ['-json', dbPath, `SELECT id, name, nodes FROM workflow_entity WHERE id = '${workflowId}';`], {
  encoding: 'utf8',
});
const nodes = JSON.parse(JSON.parse(rowsJson)[0].nodes);
for (const n of nodes.filter((x) => x.type?.includes('tollara'))) {
  console.log(n.name, JSON.stringify({ typeVersion: n.typeVersion, params: Object.keys(n.parameters ?? {}), credentials: n.credentials }));
}
