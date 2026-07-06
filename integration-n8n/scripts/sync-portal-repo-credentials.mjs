/**
 * n8n Creator Portal clones package.json.repository at the monorepo root and does
 * not honour repository.directory. It looks for n8n.credentials paths (e.g.
 * dist/credentials/*.js) relative to that root, not integration-n8n/.
 */
import { copyFileSync, existsSync, mkdirSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const packageRoot = join(dirname(fileURLToPath(import.meta.url)), '..');
const repoRoot = join(packageRoot, '..');
const sourceDir = join(packageRoot, 'dist', 'credentials');
const targetDir = join(repoRoot, 'dist', 'credentials');

if (!existsSync(sourceDir)) {
  console.error(`Missing ${sourceDir} — run tsc/build first`);
  process.exit(1);
}

mkdirSync(targetDir, { recursive: true });

let copied = 0;
for (const entry of readdirSync(sourceDir, { withFileTypes: true })) {
  if (!entry.isFile()) continue;
  copyFileSync(join(sourceDir, entry.name), join(targetDir, entry.name));
  copied += 1;
}

console.log(`Synced ${copied} credential file(s) for Creator Portal -> ${targetDir}`);
