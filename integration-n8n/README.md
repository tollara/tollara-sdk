# n8n Community Nodes – AgentVend

n8n nodes for AgentVend: webhook trigger with HMAC verification, invoke service, progress/complete (async), and validate service key.

**Package:** `n8n-nodes-agentvend`

## Install in n8n

1. Build the SDK first (from repo root): `cd sdk-js && npm run build`
2. Install this package: `cd integration-n8n && npm install && npm run build`
3. In n8n: Settings → Community Nodes → Install `n8n-nodes-agentvend` (or install from local path).

## Nodes

- **AgentVend Trigger** – Webhook that verifies HMAC using the service secret and parses `X-AgentVend-*` headers. Outputs request body and user context.
- **AgentVend Invoke** – Calls the gateway invoke API with the given service key.
- **AgentVend Progress** – Sends a progress update (use the `progressUrl` from an async invoke response).
- **AgentVend Complete** – Sends completion (use the `callbackUrl` from an async invoke response).
- **AgentVend Validate Key** – Validates a service key and returns user/plan/quota.

## Credentials

**AgentVend API**

- **Service Secret** (required) – Used for HMAC signing and verification.
- **Gateway URL** – API origin for gateway calls (e.g. `https://api.agentvend.api`).
- **Core Service URL** – API origin for validate/estimate (often the same as the gateway URL; use the values from your AgentVend deployment).
- **Usage Service URL** – API origin for usage reporting when not using absolute `progressUrl` / `callbackUrl` (often the same as the gateway URL).

## Build

```bash
npm ci
npm run build
```

Depends on `@agentvend/service-sdk` (local `../sdk-js`). Build sdk-js first.
