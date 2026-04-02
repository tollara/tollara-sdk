# AgentVend SDK (JavaScript/TypeScript)

**Package:** `@agentvend/agent-sdk`

Verify inbound HMAC, validate agent keys, report usage, progress, and completion, and poll async job status.

## API base URL

By default, all requests use the production API origin **`https://api.agentvend.api`**. Override when needed:

- **`AgentVendClient`:** pass `apiUrl`, or set environment variable **`AGENTVEND_API_URL`**
- **Standalone helpers:** pass optional `baseUrl` on each call (same default when omitted)

## Unified client (recommended)

`AgentVendClient` uses optional **`AGENTVEND_AGENT_ID`**, required **`AGENTVEND_AGENT_SECRET`** (unless passed in the constructor), and optional **`AGENTVEND_API_URL`**.

```ts
import { AgentVendClient } from '@agentvend/agent-sdk';

const client = new AgentVendClient({
  agentId: 'agent-uuid',
  agentSecret: 'secret',
});
await client.getRequestStatus(requestId, agentKey);
await client.reportUsage(userId, agentId, 1);
await client.validateAgentKey(agentKey);
```

### Verify signature and user context together

```ts
import { verifySignatureFromHeadersAndGetUserContext } from '@agentvend/agent-sdk';

const ctx = verifySignatureFromHeadersAndGetUserContext(agentSecret, headers, rawBody);
if (ctx) { /* trusted */ }
```

## Install

```bash
npm install @agentvend/agent-sdk
```

## API highlights

- `AgentVendHeaders` — canonical `X-AgentVend-*` names
- `verifyInboundHmac` / `verifySignatureFromHeaders` — inbound gateway HMAC
- `getUserContext` — parses headers (case-insensitive keys)
- `AgentVendClient` — validate key, usage, gateway polling
- `validateAgentKey({ agentKey, agentId, agentSecret, baseUrl? })` — optional `baseUrl` defaults to production
- `reportUsage`, `reportProgress`, `reportCompletion` — usage service
- `getRequestStatus`, `getRequestResult` — async job polling

## Examples

### Verify HMAC (backend)

```ts
import { verifySignatureFromHeaders, getUserContext } from '@agentvend/agent-sdk';

const agentSecret = 'your-agent-secret';
const valid = verifySignatureFromHeaders(agentSecret, req.headers, rawBodyString);
if (valid) {
  const ctx = getUserContext(req.headers);
}
```

### Validate agent key (caller)

```ts
import { validateAgentKey } from '@agentvend/agent-sdk';

const result = await validateAgentKey({
  agentKey: 'bearer-token',
  agentId: 'agent-id',
  agentSecret: 'agent-secret',
});
```

`baseUrl` is optional (defaults to `https://api.agentvend.api`). Set it for a non-production environment.

### Report usage

```ts
import { reportUsage } from '@agentvend/agent-sdk';

await reportUsage({
  userId: 'u1',
  agentId: 'a1',
  unitsUsed: 1,
  agentSecret: 'secret',
});
```

### Progress and completion (async)

URLs come from the platform (`progress_url`, `callback_url`).

```ts
import { CompletionStatus, reportProgress, reportCompletionWithResult } from '@agentvend/agent-sdk';

await reportProgress({
  progressUrl,
  requestId,
  stage: 'processing',
  percentageComplete: 50,
  agentSecret,
});
await reportCompletionWithResult({
  callbackUrl,
  requestId,
  status: CompletionStatus.Completed,
  result: 'done',
  agentSecret,
  units: 1,
});
```

### Job status / result (caller)

```ts
import { getRequestStatus, getRequestResult } from '@agentvend/agent-sdk';

const st = await getRequestStatus({ requestId, agentKey });
const res = await getRequestResult({ requestId, agentKey });
```

Optional `baseUrl` on each call if not using the default API origin.

## Build & test

```bash
npm ci
npm run build
npm test
```

## Release (npm)

Package name: **`@agentvend/agent-sdk`** ([npm scoped packages](https://docs.npmjs.com/about-scopes-and-packages)).

1. **Version** — Bump `"version"` in [`package.json`](package.json) (SemVer). npm will not let you publish the same version twice.
2. **Verify** — `npm ci`, `npm test`, and `npm run build` (or rely on `prepublishOnly`, which runs `build` on `npm publish`).
3. **Login** — `npm login` on the machine that will publish, or use an **automation token** / `NPM_TOKEN` in CI (see [access tokens](https://docs.npmjs.com/about-access-tokens) and [CI workflows](https://docs.npmjs.com/using-private-packages-in-a-ci-cd-workflow)).
4. **Publish** — From `sdk-js`:

   ```bash
   npm publish --access public
   ```

   The first publish of a **scoped** package to the public registry must use `--access public` (subsequent publishes can omit it if the package is already public).

5. **Tag** — Tag the Git commit that matches the published version.

Optional: `npm publish --dry-run` to inspect the tarball without uploading. `repository`, `files` (`dist`, `README.md`), and `prepublishOnly` are already set in `package.json`.

See [HMAC spec](https://github.com/agentvend/agentvend-sdk/blob/master/docs/hmac-spec.md) and [API spec](https://github.com/agentvend/agentvend-sdk/blob/master/docs/sdk-api-spec.md) for protocol details.
