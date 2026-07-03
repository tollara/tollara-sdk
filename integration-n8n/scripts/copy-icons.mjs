import { copyFileSync, existsSync, mkdirSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const iconSrc = join(root, 'assets', 'tollara.svg');
const nodesDist = join(root, 'dist', 'nodes');

if (!existsSync(iconSrc)) {
  console.error('Missing assets/tollara.svg');
  process.exit(1);
}

if (!existsSync(nodesDist)) {
  console.error('Missing dist/nodes — run tsc first');
  process.exit(1);
}

const credentialsDist = join(root, 'dist', 'credentials');
if (existsSync(credentialsDist)) {
  copyFileSync(iconSrc, join(credentialsDist, 'tollara.svg'));
}

for (const nodeDir of readdirSync(nodesDist, { withFileTypes: true })) {
  if (!nodeDir.isDirectory()) continue;
  const destDir = join(nodesDist, nodeDir.name);
  copyFileSync(iconSrc, join(destDir, 'tollara.svg'));
}
