import { copyFileSync, existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const iconPath = join(root, 'assets', 'tollara.svg');
const iconFileName = 'tollara-brand.svg';
const nodesSrc = join(root, 'nodes');
const nodesDist = join(root, 'dist', 'nodes');

if (!existsSync(iconPath)) {
  console.error(`Missing ${iconPath}`);
  process.exit(1);
}

function copyIconToNodeDirs(baseDir) {
  if (!existsSync(baseDir)) return;
  for (const entry of readdirSync(baseDir, { withFileTypes: true })) {
    if (!entry.isDirectory()) continue;
    copyFileSync(iconPath, join(baseDir, entry.name, iconFileName));
  }
}

copyIconToNodeDirs(nodesSrc);
copyIconToNodeDirs(nodesDist);

const credentialsDist = join(root, 'dist', 'credentials');
if (existsSync(credentialsDist)) {
  copyFileSync(iconPath, join(credentialsDist, iconFileName));
}

const credentialsSrc = join(root, 'credentials');
if (existsSync(credentialsSrc)) {
  copyFileSync(iconPath, join(credentialsSrc, iconFileName));
}

console.log(`Synced Tollara SVG icon (${readFileSync(iconPath).length} bytes) -> node folders`);
