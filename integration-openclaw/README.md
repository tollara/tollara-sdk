# OpenClaw – Tollara Plugin

OpenClaw plugin for Tollara with two modes:

- **Mode A (caller):** Invoke services on Tollara via the gateway. Use the `callAgent` tool and the skill in `skills/tollara/SKILL.md`.
- **Mode B (backend):** Act as the agent backend: verify HMAC on incoming gateway requests and report usage. Use `verifyRequest` and `reportUsageIfNeeded` with your HTTP server.

**Package:** `openclaw-tollara`

## Install

```bash
openclaw plugins install openclaw-tollara
```

Or from local path after building:

```bash
cd integration-openclaw && npm install && npm run build
openclaw plugins install ./integration-openclaw
```

## Config (openclaw.plugin.json / plugin config)

- **mode:** `caller` | `backend`
- **Caller:** `gatewayUrl`, `serviceKey`
- **Backend:** `serviceSecret`; optional `apiUrl` (Tollara API origin, default production) for usage reporting

## Mode A – Caller

```ts
import { callAgent } from 'openclaw-tollara';

const result = await callAgent(
  { gatewayUrl: 'https://api.tollara.ai', serviceKey: '...' },
  { serviceId: 'my-service', endpointId: 'run', body: { input: '...' } }
);
```

## Mode B – Backend

```ts
import { verifyRequest, reportUsageIfNeeded } from 'openclaw-tollara';

// In your HTTP handler:
const { verified, userContext, error } = verifyRequest(
  { serviceSecret: '...' },
  { body: req.body, headers: req.headers }
);
if (!verified) return res.status(401).send(error);

// ... run your agent logic ...

await reportUsageIfNeeded(
  { serviceSecret: '...' },
  { userId: userContext.userId!, serviceId: '...', unitsUsed: 1 }
);
```

## Skill

The skill at `skills/tollara/SKILL.md` teaches the OpenClaw agent when and how to use the `tollara_call_agent` tool (Mode A).
