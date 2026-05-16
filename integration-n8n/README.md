# n8n Community Nodes – Tollara

n8n nodes for Tollara: webhook trigger with HMAC verification, invoke service, progress/complete (async), and validate service key.

**Package:** `n8n-nodes-tollara`

## Upgrading from AgentVend nodes

Version **2.0.0** renames internal node and credential IDs (e.g. `tollaraInvoke`, `tollaraApi`). Existing workflows built against `n8n-nodes-agentvend` must be recreated or reconfigured after upgrade.

## Install in n8n

1. Build the SDK first (from repo root): `cd sdk-js && npm run build`
2. Install this package: `cd integration-n8n && npm install && npm run build`
3. In n8n: Settings → Community Nodes → Install `n8n-nodes-tollara` (or install from local path).

## Nodes

- **Tollara Trigger** – Webhook that verifies HMAC using the service secret and parses `X-Tollara-*` headers. Outputs request body and user context.
- **Tollara Invoke** – Calls the gateway invoke API with the given service key.
- **Tollara Progress** – Sends a progress update (use the `progressUrl` from an async invoke response).
- **Tollara Complete** – Sends completion (use the `callbackUrl` from an async invoke response).
- **Tollara Validate Key** – Validates a service key and returns user/plan/quota.

## Credentials

**Tollara API**

- **Service Secret** (required) – Used for HMAC signing and verification.
- **Gateway URL** – API origin for gateway calls (e.g. `https://api.tollara.ai`).
- **Core Service URL** – API origin for validate/estimate (often the same as the gateway URL; use the values from your Tollara deployment).
- **Usage Service URL** – API origin for usage reporting when not using absolute `progressUrl` / `callbackUrl` (often the same as the gateway URL).

## Build

```bash
npm ci
npm run build
```

Depends on `@tollara/service-sdk` (local `../sdk-js`). Build sdk-js first.
