# OpenClaw – AgentVend Plugin

OpenClaw plugin for AgentVend with two modes:

- **Mode A (caller):** Invoke agents on AgentVend via the gateway. Use the `callAgent` tool and the skill in `skills/agentvend/SKILL.md`.
- **Mode B (backend):** Act as the agent backend: verify HMAC on incoming gateway requests and report usage. Use `verifyRequest` and `reportUsageIfNeeded` with your HTTP server.

**Package:** `openclaw-agentvend`

## Install

```bash
openclaw plugins install openclaw-agentvend
```

Or from local path after building:

```bash
cd integration-openclaw && npm install && npm run build
openclaw plugins install ./integration-openclaw
```

## Config (openclaw.plugin.json / plugin config)

- **mode:** `caller` | `backend`
- **Caller:** `gatewayUrl`, `agentKey`; optional: `coreServiceUrl`, `usageServiceUrl`
- **Backend:** `agentSecret`, `usageServiceUrl`; optional: `coreServiceUrl`

## Mode A – Caller

```ts
import { callAgent } from 'openclaw-agentvend';

const result = await callAgent(
  { gatewayUrl: 'https://gateway.example.com', agentKey: '...' },
  { agentId: 'my-agent', endpointId: 'run', body: { input: '...' } }
);
```

## Mode B – Backend

```ts
import { verifyRequest, reportUsageIfNeeded } from 'openclaw-agentvend';

// In your HTTP handler:
const { verified, userContext, error } = verifyRequest(
  { agentSecret: '...' },
  { body: req.body, headers: req.headers }
);
if (!verified) return res.status(401).send(error);

// ... run your agent logic ...

await reportUsageIfNeeded(
  { usageServiceUrl: '...', agentSecret: '...' },
  { userId: userContext.userId!, agentId: '...', unitsUsed: 1 }
);
```

## Skill

The skill at `skills/agentvend/SKILL.md` teaches the OpenClaw agent when and how to use the `agentvend_call_agent` tool (Mode A).
