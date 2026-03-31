# AgentVend SDK (TypeScript)

Client SDK for AgentVend: verify HMAC on incoming gateway requests, validate agent keys, report usage, progress and completion, and poll async job status on the gateway. This package is implemented in **TypeScript**, compiled to **CommonJS** for Node and browsers, and ships **declaration files** (`.d.ts`) so JavaScript and TypeScript consumers get the same API and types.

**Package:** `@agentvend/agent-sdk`

## Configuration

### Recommended: single `AgentVendClient`

Use **`AgentVendClient`** with one API origin. The SDK appends the path prefixes from [sdk-api-spec.md](../docs/sdk-api-spec.md) (default deployment). Override prefixes when using ECS layouts or local Docker.

| Setting | Default | Notes |
|--------|---------|--------|
| API origin | **`https://api.agentvend.api`** (`DEFAULT_API_URL`) | Override with `apiUrl`, or env **`AGENTVEND_API_URL`** for staging or local stacks |
| Agent ID | From env **`AGENTVEND_AGENT_ID`**, or constructor `agentId` | Optional if Core can infer the agent from the key |
| Agent secret | From env **`AGENTVEND_AGENT_SECRET`**, or constructor `agentSecret` | **Required** (Usage HMAC + Core response verification) |
| Core prefix | `/api/v1` | `corePathPrefix` for non-default paths (e.g. ECS) |
| Gateway prefix | `/api` | `gatewayPathPrefix` for non-default paths |
| Usage prefix | `/api/usage` | `usagePathPrefix` before `/report` |

Split hosts (optional): `coreApiUrl`, `gatewayApiUrl`, and `usageApiUrl` each default to the main API URL when unset.

**Progress / completion** still use the **full** `progressUrl` / `callbackUrl` strings from the gateway (including query params).

See [api-overview.md](../docs/api-overview.md).

### Environment variables

Constructor options win when set; otherwise the SDK reads `process.env` when available:

| Variable | Purpose |
|----------|---------|
| **`AGENTVEND_API_URL`** | Optional. Overrides the default production API origin when set. |
| **`AGENTVEND_AGENT_ID`** | Agent UUID if you omit `agentId` (optional). |
| **`AGENTVEND_AGENT_SECRET`** | Agent secret if you omit `agentSecret` (**required** one way or the other). |

In code, names are also available as `AgentVendClient.ENV_API_URL`, `ENV_AGENT_ID`, and `ENV_AGENT_SECRET`. The default base URL is `AgentVendClient.DEFAULT_API_URL`.

### Low-level helpers

`validateAgentKey`, `reportUsage`, `reportProgress`, `reportCompletion`, `getRequestStatus`, and `getRequestResult` accept explicit base URLs if you do not use `AgentVendClient`.

### Unified client example

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
if (ctx) {
  /* trusted */
}
```

## Install

```bash
npm install @agentvend/agent-sdk
```

JavaScript projects use the same package; types are optional at compile time.

## API highlights

- `AgentVendHeaders` — canonical `X-AgentVend-*` names.
- `verifyInboundHmac(agentSecret, InboundHmacRequest)` / `verifySignatureFromHeaders(agentSecret, headers, payload)` — inbound gateway HMAC.
- `getUserContext(headers)` — parses headers (case-insensitive keys).
- `AgentVendClient` — env + unified validate / usage / gateway.
- `validateAgentKey({ coreServiceUrl, agentKey, agentId, agentSecret })`
- `reportUsage` (optional `usagePathPrefix`), `reportProgress`, `reportCompletion`
- `getRequestStatus`, `getRequestResult` — gateway polling.

Exported types include `UserContext`, `InboundHmacRequest`, `AgentVendClientOptions`, `UsageReportResponse`, and others (see `src/index.ts`).

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

## Build and test

From this directory:

```bash
npm ci
npm run build
npm test
```

The build runs `tsc` with `declaration: true`; publishable artifacts live under `dist/`.

See [HMAC spec](../docs/hmac-spec.md) and [API spec](../docs/sdk-api-spec.md).
