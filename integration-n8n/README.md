# n8n Community Nodes – AgentVend

n8n nodes for AgentVend: webhook trigger with HMAC verification, invoke agent, progress/complete (async), and validate agent key.

**Package:** `n8n-nodes-agentvend`

## Install in n8n

1. Build the SDK first (from repo root): `cd sdk-js && npm run build`
2. Install this package: `cd integration-n8n && npm install && npm run build`
3. In n8n: Settings → Community Nodes → Install `n8n-nodes-agentvend` (or install from local path).

## Nodes

- **AgentVend Trigger** – Webhook that verifies HMAC using the agent secret and parses `X-AgentVend-*` headers. Outputs request body and user context.
- **AgentVend Invoke** – Calls the gateway `POST /api/agent/{agentId}/endpoint/{endpointId}/invoke` with the given agent key.
- **AgentVend Progress** – Sends a progress update to the usage service (use the `progressUrl` from an async invoke response).
- **AgentVend Complete** – Sends completion to the usage service (use the `callbackUrl` from an async invoke response).
- **AgentVend Validate Key** – Validates an agent key via the core service and returns user/plan/quota.

## Credentials

**AgentVend API**

- **Agent Secret** (required) – Used for HMAC signing and verification.
- **Gateway URL** – Base URL of the gateway (e.g. `https://api.agentvend.api`).
- **Core Service URL** – Base URL of the core service (e.g. `https://api.agentvend.api/core/api/v1`).
- **Usage Service URL** – Base URL of the usage service (e.g. `https://api.agentvend.api`).

## Build

```bash
npm ci
npm run build
```

Depends on `@agentvend/agent-sdk` (local `../sdk-js`). Build sdk-js first.
