import * as esbuild from 'esbuild';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const outfile = join(root, 'dist', 'lib', 'tollaraSdk.js');

await esbuild.build({
  entryPoints: [join(root, 'lib', 'tollaraSdk.ts')],
  bundle: true,
  platform: 'node',
  format: 'cjs',
  outfile,
  target: 'es2019',
  logLevel: 'info',
});

console.log(`Bundled @tollara/service-sdk -> ${outfile}`);
