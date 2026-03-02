# n8n Community Nodes – Marketplace (Agent Hub)

n8n nodes for Agent Hub: webhook trigger with HMAC verification, invoke agent, progress/complete (async), and validate agent key.

**Package (placeholder):** `n8n-nodes-marketplace`

## Install in n8n

1. Build the SDK first (from repo root): `cd sdk-js && npm run build`
2. Install this package: `cd integration-n8n && npm install && npm run build`
3. In n8n: Settings → Community Nodes → Install `n8n-nodes-marketplace` (or install from local path).

## Nodes

- **Marketplace Trigger** – Webhook that verifies HMAC using the agent secret and parses `X-Marketplace-*` headers. Outputs request body and user context.
- **Marketplace Invoke** – Calls the gateway `POST /api/agent/{agentId}/endpoint/{endpointId}/invoke` with the given agent key.
- **Marketplace Progress** – Sends a progress update to the usage service (use the `progressUrl` from an async invoke response).
- **Marketplace Complete** – Sends completion to the usage service (use the `callbackUrl` from an async invoke response).
- **Marketplace Validate Key** – Validates an agent key via the core service and returns user/plan/quota.

## Credentials

**Marketplace (Agent Hub) API**

- **Agent Secret** (required) – Used for HMAC signing and verification.
- **Gateway URL** – Base URL of the gateway (e.g. `http://localhost:8083`).
- **Core Service URL** – Base URL of the core service (e.g. `http://localhost:8081/api/v1`).
- **Usage Service URL** – Base URL of the usage service (e.g. `http://localhost:8084`).

## Build

```bash
npm ci
npm run build
```

Depends on `@marketplace/agent-sdk` (local `../sdk-js`). Build sdk-js first.
