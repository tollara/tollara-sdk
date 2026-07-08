/**
 * Assembles a root-level publish payload for the dedicated mirror repo
 * (tollara/n8n-nodes-tollara). The n8n Creator Portal clones the repo named in
 * package.json.repository and looks for the package (package.json + dist) at the
 * repo ROOT — it does not honour repository.directory. This monorepo can never be
 * that root, so we mirror integration-n8n's built output to a repo whose root IS
 * the package, and publish from there.
 *
 * Run after `npm run build`. Output goes to integration-n8n/mirror/ (gitignored).
 */
import {
  cpSync,
  existsSync,
  mkdirSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const out = join(root, 'mirror');
const MIRROR_SLUG = 'tollara/n8n-nodes-tollara';

const pkg = JSON.parse(readFileSync(join(root, 'package.json'), 'utf8'));

if (!existsSync(join(root, 'dist', 'credentials'))) {
  console.error('dist/ is missing — run "npm run build" before build-mirror.');
  process.exit(1);
}

rmSync(out, { recursive: true, force: true });
mkdirSync(join(out, '.github', 'workflows'), { recursive: true });

cpSync(join(root, 'dist'), join(out, 'dist'), { recursive: true });
cpSync(join(root, 'index.js'), join(out, 'index.js'));
cpSync(join(root, 'README.md'), join(out, 'README.md'));
cpSync(join(root, 'LICENSE'), join(out, 'LICENSE'));
cpSync(join(root, 'example-workflows'), join(out, 'example-workflows'), { recursive: true });
rmSync(join(out, 'example-workflows', 'local'), { recursive: true, force: true });

cpSync(
  join(root, 'mirror-template', 'publish.yml'),
  join(out, '.github', 'workflows', 'publish.yml'),
);

// Published package.json: repository must point at the mirror root, with no
// `directory`. Build/release tooling is dropped because the mirror only publishes
// prebuilt output (no rebuild, no sdk-js dependency at publish time).
const mirrorPkg = {
  name: pkg.name,
  version: pkg.version,
  description: pkg.description,
  keywords: pkg.keywords,
  license: pkg.license,
  homepage: `https://github.com/${MIRROR_SLUG}#readme`,
  bugs: { url: `https://github.com/${MIRROR_SLUG}/issues` },
  author: pkg.author,
  repository: { type: 'git', url: `git+https://github.com/${MIRROR_SLUG}.git` },
  engines: pkg.engines,
  main: pkg.main,
  files: pkg.files,
  n8n: pkg.n8n,
  peerDependencies: pkg.peerDependencies,
  publishConfig: pkg.publishConfig,
};

writeFileSync(join(out, 'package.json'), `${JSON.stringify(mirrorPkg, null, 2)}\n`);

console.log(`Mirror payload ready at ${out} (v${pkg.version})`);
