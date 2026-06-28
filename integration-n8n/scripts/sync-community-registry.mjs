/**
 * Emit SQL to sync n8n installed_packages / installed_nodes with local package.json + dist.
 * Used by docker/sync-community-db.ps1 against the n8n_data volume.
 */
import { readFileSync } from 'node:fs';
import { join, dirname, basename } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const pkg = JSON.parse(readFileSync(join(root, 'package.json'), 'utf8'));
const packageName = pkg.name;
const version = pkg.version;
const nodePaths = pkg.n8n?.nodes ?? [];

const nodes = nodePaths.map((relPath) => {
  const abs = join(root, relPath);
  const src = readFileSync(abs, 'utf8');
  const displayName = src.match(/displayName:\s*['"]([^'"]+)['"]/)?.[1];
  const name = src.match(/^\s*name:\s*['"]([^'"]+)['"]/m)?.[1];
  const version = Number(src.match(/^\s*version:\s*(\d+)/m)?.[1] ?? 1);
  if (!displayName || !name) {
    throw new Error(`Could not parse node metadata from ${relPath}`);
  }
  return { displayName, type: `${packageName}.${name}`, version };
});

const esc = (s) => s.replace(/'/g, "''");

const lines = [
  `DELETE FROM installed_nodes WHERE package = '${esc(packageName)}';`,
  `DELETE FROM installed_packages WHERE packageName = '${esc(packageName)}';`,
  `INSERT INTO installed_packages (packageName, installedVersion, authorName, authorEmail, createdAt, updatedAt)`,
  `VALUES ('${esc(packageName)}', '${esc(version)}', NULL, NULL, datetime('now'), datetime('now'));`,
];

for (const node of nodes) {
  lines.push(
    `INSERT INTO installed_nodes (name, type, latestVersion, package) VALUES ('${esc(node.displayName)}', '${esc(node.type)}', ${node.version}, '${esc(packageName)}');`,
  );
}

process.stdout.write(lines.join('\n') + '\n');
