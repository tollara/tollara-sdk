# Agent Hub SDK (JavaScript/TypeScript)

**Package (placeholder):** `@marketplace/agent-sdk`

Verify HMAC, validate agent keys, report usage, and send progress/completion for Agent Hub.

## Install

```bash
npm install @marketplace/agent-sdk
```

## API

- **verifySignature(agentSecret, input)** — Validates HMAC on an inbound gateway request (payload + timestamp + userContextString).
- **getUserContext(headers)** — Parses `X-Marketplace-*` headers into `UserContext`.
- **validateAgentKey(params)** — Calls core-service `/agent-keys/validate`, verifies response HMAC, returns user/plan/quota or null.
- **reportProgress(params)** — POST to progress URL with signed body.
- **reportCompletion(params)** — POST to callback URL with signed body.
- **reportUsage(params)** — POST to usage service report endpoint with signed body.

## Minimal example

### Verify HMAC and get user context (backend)

```ts
import { verifySignature, getUserContext } from '@marketplace/agent-sdk';

const agentSecret = 'your-agent-secret';
const valid = verifySignature(agentSecret, {
  signature: req.headers['x-marketplace-signature'],
  timestamp: req.headers['x-marketplace-timestamp'],
  payload: req.body,
  userId: req.headers['x-marketplace-user-id'],
  plan: req.headers['x-marketplace-plan'],
  roles: (req.headers['x-marketplace-roles'] || '').split(','),
  quotaRemaining: req.headers['x-marketplace-quota-remaining'],
});
if (valid) {
  const ctx = getUserContext(req.headers);
  console.log(ctx.userId, ctx.plan);
}
```

### Validate agent key (caller)

```ts
import { validateAgentKey } from '@marketplace/agent-sdk';

const result = await validateAgentKey({
  coreServiceUrl: 'https://core.example.com/api/v1',
  agentKey: 'bearer-token',
  agentId: 'agent-id',
  agentSecret: 'agent-secret',
});
if (result) console.log(result.userId, result.quotaRemaining);
```

### Report usage (backend)

```ts
import { reportUsage } from '@marketplace/agent-sdk';

await reportUsage({
  usageServiceUrl: 'https://usage.example.com',
  userId: 'u1',
  agentId: 'a1',
  unitsUsed: 1,
  agentSecret: 'secret',
});
```

See [HMAC spec](../docs/hmac-spec.md) and [API overview](../docs/api-overview.md) in the repo `docs/`.

## Build & test

```bash
npm ci
npm run build
npm test
```
