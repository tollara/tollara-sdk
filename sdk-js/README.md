# AgentVend SDK (JavaScript/TypeScript)

**Package:** `@agentvend/agent-sdk`

Verify HMAC, validate agent keys, report usage, progress, completion, and poll async job status on the gateway.

## Configuration (base URLs)

**Unified `AgentVendClient`:** the API origin defaults to **`https://api.agentvend.api`** (`DEFAULT_API_URL`). Override with `apiUrl` or **`AGENTVEND_API_URL`** when pointing at staging or a local stack. Default path prefixes match [sdk-api-spec.md](../docs/sdk-api-spec.md); override `corePathPrefix`, `gatewayPathPrefix`, or `usagePathPrefix` only for non-standard deployments.

**Low-level helpers** take explicit bases: **Core** `coreServiceUrl` (e.g. `{origin}/api/v1` as joined by the unified client), **Usage** `usageServiceUrl`, **Gateway** `gatewayBaseUrl` + `gatewayPathPrefix`. **Progress / completion** always use the full `progressUrl` / `callbackUrl` from the platform.

See [api-overview.md](../docs/api-overview.md).

### Unified client

`AgentVendClient` uses optional `AGENTVEND_API_URL`, optional `AGENTVEND_AGENT_ID`, and required `AGENTVEND_AGENT_SECRET` (unless passed in the constructor). Default prefixes: `/api/v1`, `/api`, `/api/usage`.

```ts
import { AgentVendClient } from '@agentvend/agent-sdk';

const client = new AgentVendClient({
  agentId: 'agent-uuid',
  agentSecret: 'secret',
});
await client.getRequestStatus(requestId, agentKey);
await client.reportUsage(userId, agentId, 1);
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

- `AgentVendHeaders` — canonical `X-AgentVend-*` names.
- `verifyInboundHmac(agentSecret, InboundHmacRequest)` / `verifySignatureFromHeaders(agentSecret, headers, payload)` — inbound gateway HMAC.
- `getUserContext(headers)` — parses headers (case-insensitive keys).
- `AgentVendClient` — env + unified validate / usage / gateway.
- `validateAgentKey({ coreServiceUrl, agentKey, agentId, agentSecret })`
- `reportUsage` (optional `usagePathPrefix`), `reportProgress`, `reportCompletion`
- `getRequestStatus`, `getRequestResult` — gateway polling.

## Examples

### Verify HMAC (backend)

```ts
import { verifySignatureFromHeaders, getUserContext, AgentVendHeaders } from '@agentvend/agent-sdk';

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
  coreServiceUrl: 'https://api.agentvend.api/core/api/v1',
  agentKey: 'bearer-token',
  agentId: 'agent-id',
  agentSecret: 'agent-secret',
});
```

### Report usage

```ts
import { reportUsage } from '@agentvend/agent-sdk';

await reportUsage({
  usageServiceUrl: 'https://api.agentvend.api',
  userId: 'u1',
  agentId: 'a1',
  unitsUsed: 1,
  agentSecret: 'secret',
});
```

### Progress and completion (async)

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

const st = await getRequestStatus({
  gatewayBaseUrl: 'https://api.agentvend.api',
  gatewayPathPrefix: '/api',
  requestId,
  agentKey,
});
const res = await getRequestResult({
  gatewayBaseUrl: 'https://api.agentvend.api',
  gatewayPathPrefix: '/api',
  requestId,
  agentKey,
});
```

## Build & test

```bash
npm ci
npm run build
npm test
```

See [HMAC spec](../docs/hmac-spec.md) and [API spec](../docs/sdk-api-spec.md).
