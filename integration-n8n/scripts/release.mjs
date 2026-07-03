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
  return result.status ?? 1;
}

function runOrExit(cmd, args, env = {}) {
  const code = run(cmd, args, env);
  if (code !== 0) {
    process.exit(code);
  }
}

function detectPackageManager() {
  if (existsSync(join(root, 'pnpm-lock.yaml'))) return 'pnpm';
  if (existsSync(join(root, 'yarn.lock'))) return 'yarn';
  return 'npm';
}

const pm = detectPackageManager();
const publishFromLocal = process.argv.includes('--publish');

if (process.env.GITHUB_ACTIONS) {
  runOrExit(pm, ['run', 'lint']);
  runOrExit(pm, ['run', 'build']);
  runOrExit('npm', ['publish'], {
    RELEASE_MODE: 'true',
    NPM_CONFIG_PROVENANCE: 'true',
  });
  process.exit(0);
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

runOrExit(pm, releaseItArgs, { RELEASE_MODE: 'true' });
