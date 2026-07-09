/**
 * Assembles a root-level publish payload for the dedicated mirror repo
 * (tollara/n8n-nodes-tollara). The n8n Creator Portal clones the repo named in
 * package.json.repository and looks for the package at the repo ROOT — it does
 * not honour repository.directory. This monorepo can never be that root, so we
 * mirror integration-n8n to a repo whose root IS the package, and publish there.
 *
 * The payload contains BOTH:
 *   - the TypeScript SOURCE (credentials/, nodes/, lib/, tsconfig.json) at the
 *     repo root. The portal resolves package.json's n8n.credentials/n8n.nodes
 *     compiled paths (dist/credentials/Foo.credentials.js) back to their source
 *     (credentials/Foo.credentials.ts) and fails with "Can't find credential
 *     file in repo" if the .ts source is absent (mirrors n8n-nodes-starter).
 *   - the built dist/. The mirror repo's publish workflow runs `npm publish`
 *     without rebuilding, so the compiled output must be committed. Source .ts
 *     is not in package.json.files, so the npm tarball stays dist-only.
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
mkdirSync(out, { recursive: true });

cpSync(join(root, 'dist'), join(out, 'dist'), { recursive: true });
cpSync(join(root, 'index.js'), join(out, 'index.js'));
cpSync(join(root, 'README.md'), join(out, 'README.md'));
cpSync(join(root, 'LICENSE'), join(out, 'LICENSE'));
cpSync(join(root, 'example-workflows'), join(out, 'example-workflows'), { recursive: true });
rmSync(join(out, 'example-workflows', 'local'), { recursive: true, force: true });

// TypeScript source at the repo root so the Creator Portal can resolve the
// credential/node source files referenced (compiled) in package.json.n8n.
for (const dir of ['credentials', 'nodes', 'lib']) {
  cpSync(join(root, dir), join(out, dir), { recursive: true });
}
cpSync(join(root, 'tsconfig.json'), join(out, 'tsconfig.json'));

// The mirror's own .github/workflows/publish.yml is committed to the mirror repo
// once by hand (see mirror-template/publish.yml). The sync token only has
// Contents write and cannot push workflow files, so the sync must never touch
// .github/ — it is excluded from the rsync in the sync workflow.

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
