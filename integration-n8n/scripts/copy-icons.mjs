import { copyFileSync, existsSync, mkdirSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const iconSrc = join(root, 'assets', 'tollara.png');
const nodesDist = join(root, 'dist', 'nodes');

if (!existsSync(iconSrc)) {
  console.error('Missing assets/tollara.png');
  process.exit(1);
}

if (!existsSync(nodesDist)) {
  console.error('Missing dist/nodes — run tsc first');
  process.exit(1);
}

for (const nodeDir of readdirSync(nodesDist, { withFileTypes: true })) {
  if (!nodeDir.isDirectory()) continue;
  const destDir = join(nodesDist, nodeDir.name);
  copyFileSync(iconSrc, join(destDir, 'tollara.png'));
}
