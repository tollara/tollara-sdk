import { spawnSync } from 'node:child_process';
import { readdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const testDir = join(root, 'test-dist', 'tests', 'integration-n8n', 'lib');
const libTests = readdirSync(testDir)
  .filter((name) => name.endsWith('.test.js'))
  .map((name) => join('test-dist', 'tests', 'integration-n8n', 'lib', name));

const files = [...libTests, join('scripts', 'apply-local-fixture.test.mjs')];
const result = spawnSync(process.execPath, ['--test', ...files], {
  cwd: root,
  stdio: 'inherit',
  shell: process.platform === 'win32',
});

process.exit(result.status ?? 1);
