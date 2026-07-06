import * as esbuild from 'esbuild';
import { rm, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const outfile = join(root, 'dist', 'lib', 'tollaraSdk.js');
const entry = join(root, 'dist', '.tollaraSdkBundleEntry.ts');

await writeFile(
  entry,
  [
    "export { CompletionStatus } from '../../sdk-js/src/completionStatus';",
    "export { estimateUsage, validateServiceKeyWithOutcome } from '../../sdk-js/src/validationClient';",
    "export { getRequestResult, getRequestStatus } from '../../sdk-js/src/gatewayClient';",
    "export { getUserContext, grantAccess, verifySignatureFromHeaders } from '../../sdk-js/src/verifier';",
    "export { invokeService } from '../../sdk-js/src/gatewayInvoke';",
    "export { reportCompletion, reportProgress, reportUsage } from '../../sdk-js/src/usageClient';",
    '',
  ].join('\n')
);

try {
  await esbuild.build({
    entryPoints: [entry],
    bundle: true,
    platform: 'node',
    format: 'cjs',
    outfile,
    target: 'es2019',
    logLevel: 'info',
  });
} finally {
  await rm(entry, { force: true });
}

console.log(`Bundled selected @tollara/service-sdk helpers -> ${outfile}`);
