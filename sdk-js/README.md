# AgentVend SDK (JavaScript/TypeScript)

**Package:** `@agentvend/agent-sdk`

Verify HMAC, validate agent keys, report usage, progress, completion, and poll async job status on the gateway.

## Configuration (base URLs)

The SDK **never hardcodes** production URLs. You pass:

- **Core:** `coreServiceUrl` (e.g. `https://api.agentvend.api/core/api/v1`) for validate.
- **Usage:** `usageServiceUrl` for `reportUsage` (appends `/api/usage/report`). Match your deployment to [sdk-api-spec.md](../docs/sdk-api-spec.md) §3 (default vs ECS prefixes).
- **Gateway:** `gatewayBaseUrl` + `gatewayPathPrefix` for `getRequestStatus` / `getRequestResult` (`/api` vs `/gateway/api/v1`).
- **Progress / completion:** full `progressUrl` and `callbackUrl` strings from the async invoke response.

See [api-overview.md](../docs/api-overview.md).

### Unified client

`AgentVendClient` mirrors Java: `AGENTVEND_API_URL`, optional `AGENTVEND_AGENT_ID` / `AGENTVEND_AGENT_SECRET`, default prefixes `/api/v1`, `/api`, `/api/usage`. Pass options in the constructor to override env. Use `usagePathPrefix` for non-default Usage layouts.

```ts
import { AgentVendClient } from '@agentvend/agent-sdk';

const client = new AgentVendClient({
  apiUrl: 'https://api.agentvend.api',
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
