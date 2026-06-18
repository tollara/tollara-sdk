# n8n Community Nodes – Tollara

n8n nodes for Tollara: webhook trigger with HMAC verification, gateway invoke, async job polling, progress/complete, validate service key, report usage, and estimate usage.

**Package:** `n8n-nodes-tollara`

## Install in n8n

1. Install this package: `cd integration-n8n && npm install && npm run build`
2. In n8n: Settings → Community Nodes → Install `n8n-nodes-tollara` (or install from local path).

The `@tollara/service-sdk` dependency is installed from npm automatically.

## Nodes

- **Tollara Trigger** – Webhook that verifies HMAC using the service secret and parses `X-Tollara-*` headers. Outputs request body and user context.
- **Tollara Invoke** – Invoke a service endpoint (sync or async). Supports GET, POST, PUT, DELETE.
- **Tollara Job Status** – Poll async job status by request ID.
- **Tollara Job Result** – Fetch async job result by request ID.
- **Tollara Progress** – Send a progress update (use the `progressUrl` from an async invoke response).
- **Tollara Complete** – Send completion (use the `callbackUrl` from an async invoke response).
- **Tollara Validate Key** – Validate a service key and return user/plan/quota.
- **Tollara Report Usage** – Report usage units for a user and service.
- **Tollara Estimate Usage** – Estimate usage cost and quota for a service key.

## Example workflows

**Caller async:** Tollara Invoke (async) → Tollara Job Status → Tollara Job Result

**Backend async:** Tollara Trigger → [your logic] → Tollara Progress → Tollara Complete

## Credentials

**Tollara API**

- **Service Secret** (required) – Used for HMAC signing and verification.
- **API URL** (optional) – Leave blank for production. Override only for local or dev testing.

## Build

```bash
npm install
npm run build
```
