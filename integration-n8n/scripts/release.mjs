import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');

function run(cmd, args, env = {}) {
  const result = spawnSync(cmd, args, {
    cwd: root,
    stdio: 'inherit',
    shell: process.platform === 'win32',
    env: { ...process.env, ...env },
  });
  process.exit(result.status ?? 1);
}

function detectPackageManager() {
  if (existsSync(join(root, 'pnpm-lock.yaml'))) return 'pnpm';
  if (existsSync(join(root, 'yarn.lock'))) return 'yarn';
  return 'npm';
}

const pm = detectPackageManager();
const publishFromLocal = process.argv.includes('--publish');

if (process.env.GITHUB_ACTIONS) {
  run(pm, ['run', 'lint']);
  run(pm, ['run', 'build']);
  run('npm', ['publish'], {
    RELEASE_MODE: 'true',
    NPM_CONFIG_PROVENANCE: 'true',
  });
}

if (publishFromLocal) {
  console.warn(
    'Publishing directly from your machine will not include npm provenance, which is required for n8n Cloud starting May 1 2026.\n' +
      'Consider switching to GitHub Actions publishing. See: https://docs.n8n.io/integrations/creating-nodes/deploy/submit-community-nodes/',
  );
} else {
  console.info(
    'The node will not be published to NPM locally. GitHub Actions publishes on tag push with provenance.',
  );
}

const releaseItArgs = ['exec', '--', 'release-it', '-n'];
if (publishFromLocal) {
  releaseItArgs.push('--npm.publish');
} else {
  releaseItArgs.push('--npm.publish=false');
}

if (!process.env.GITHUB_TOKEN && !process.env.GH_TOKEN) {
  releaseItArgs.push('--github.release=false');
}

run(pm, releaseItArgs, { RELEASE_MODE: 'true' });
