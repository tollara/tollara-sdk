# n8n Community Nodes – Tollara

n8n nodes for Tollara: verify inbound HMAC from the n8n Webhook node, gateway invoke, async job polling, progress/complete, validate service key, report usage, and estimate usage.

**Package:** `n8n-nodes-tollara`

## Install in n8n

1. Install this package: `cd integration-n8n && npm install && npm run build`
2. In n8n: Settings → Community Nodes → Install `n8n-nodes-tollara` (or install from local path).

The `@tollara/service-sdk` dependency is installed from npm automatically.

## Nodes

- **Tollara Verify Request** – Verify Tollara HMAC on output from the n8n **Webhook** node. Passes through all webhook fields (`headers`, `params`, `query`, `body`, binary) and adds `userContext`.
- **Tollara Invoke** – Invoke a service endpoint (sync or async). Supports GET, POST, PUT, DELETE.
- **Tollara Job Status** – Poll async job status by request ID.
- **Tollara Job Result** – Fetch async job result by request ID.
- **Tollara Progress** – Send a progress update (use the `progressUrl` from an async invoke response).
- **Tollara Complete** – Send completion (use the `callbackUrl` from an async invoke response).
- **Tollara Validate Key** – Validate a service key and return user/plan/quota. Place after the n8n **Webhook** node; reads `Authorization: Bearer` automatically. Set **Service ID** once in credentials (Service Workspace → your service → Settings).
- **Tollara Report Usage** – Report usage units for a user and service.
- **Tollara Estimate Usage** – Estimate usage cost and quota for a service key.

## Example workflows

Import-ready demo workflows live in [`example-workflows/`](example-workflows/README.md):

| Workflow | Mode |
|----------|------|
| `url-metadata-sync.json` | Proxied sync — fetch page title/description |
| `topic-brief-async-backend.json` | Proxied async — Wikipedia brief + Progress/Complete |
| `subscriber-echo-non-proxied.json` | Non-proxied — Validate Key + echo + Report Usage |

**Caller async:** Tollara Invoke (async) → Tollara Job Status → Tollara Job Result

**Backend async:** Webhook → Tollara Verify Request → [your logic] → Tollara Progress → Tollara Complete

Enable **Raw Body** on the Webhook node for reliable HMAC verification. Point your Tollara service at the Webhook production URL.

## Credentials

**Tollara API**

- **Service Secret** (required) – Used for HMAC signing and verification.
- **Service ID** (optional) – Your service UUID from the Tollara Service Workspace (service settings). Avoids re-entering it on each Validate Key node.
- **API URL** (optional) – Leave blank for production. Default base URL for all services.
- **Core / Usage / Gateway API URL** (optional) – Per-service overrides for local dev when services run on different ports. Each falls back to **API URL**, then the production default.

**Local Docker example:** leave **API URL** blank and set **Core API URL** to `http://host.docker.internal:8081` and **Usage API URL** to `http://host.docker.internal:8084` (and **Gateway API URL** to `http://host.docker.internal:8083` if you use Invoke). One credential works for the whole workflow.

## Build

```bash
npm install
npm run build
```

## Local testing (Docker)

Self-hosted n8n can install unverified community nodes from npm (unlike n8n Cloud).

**Local dev (no npm publish):** the Docker setup bind-mounts your built `integration-n8n` folder. Changes take effect after `npm run build` and `docker compose restart`.

From `integration-n8n/docker`:

```powershell
cd integration-n8n\docker
.\start.ps1
```

Or manually:

```powershell
cd integration-n8n
npm install
npm run build
cd docker
docker compose up -d --force-recreate
```

Open **http://localhost:5678**, create an owner account, then add nodes — search **Tollara**.

Workflow data persists in the `n8n_data` Docker volume. The Tollara package is loaded from your repo via bind mount, not from npm.

Stop: `docker compose down` (from the `docker` folder).
