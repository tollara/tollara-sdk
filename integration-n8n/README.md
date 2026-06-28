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
- **Tollara Validate Key** – Validate a service key and return user/plan/quota. Place after the n8n **Webhook** node; reads `Authorization: Bearer` automatically. Set **Service Secret** and **Service ID** on the node.
- **Tollara Report Usage** – Report usage units for a user and service.
- **Tollara Estimate Usage** – Estimate usage cost and quota for a service key.

## Example workflows

Import-ready demo workflows live in [`example-workflows/`](example-workflows/README.md):

| Workflow | Mode |
|----------|------|
| `backend-url-metadata-sync.json` | Proxied sync backend — fetch page title/description |
| `backend-topic-brief-async.json` | Proxied async backend — Wikipedia brief + Progress/Complete |
| `backend-echo-non-proxied.json` | Non-proxied backend — Validate Key + echo + Report Usage |
| `subscriber-url-metadata-sync.json` | Subscriber — Invoke sync (URL Metadata) |
| `subscriber-url-metadata-estimate.json` | Subscriber — Estimate Usage + Invoke sync |
| `subscriber-topic-brief-async.json` | Subscriber — Invoke async + Job Status + Job Result |

**Caller async:** Tollara Invoke (async) → Tollara Job Status → Tollara Job Result

**Backend async:** Webhook → Tollara Verify Request → [your logic] → Tollara Progress → Tollara Complete

Enable **Raw Body** on the Webhook node for reliable HMAC verification. Point your Tollara service at the Webhook production URL.

## API endpoints (optional)

**Production:** leave **Set API Endpoints** disabled on each node. The SDK uses production Tollara URLs automatically. No n8n credential is required.

**Custom / local dev:** on nodes that call Tollara APIs (Invoke, Validate Key, Progress, etc.), enable **Set API Endpoints** and fill in the URLs you need. **Tollara Verify Request** only needs **Service Secret** — it does not call Tollara APIs.

Set **Service Secret** and **Service ID** on each Tollara node (required where those fields appear).

## Build

```bash
npm install
npm run build
```

## Local testing (Docker)

Self-hosted n8n can install unverified community nodes from npm (unlike n8n Cloud).

**Local dev (no npm publish):** the Docker setup bind-mounts your built `integration-n8n` folder and `sdk-js` (for the `file:../sdk-js` dependency). One command builds everything and redeploys n8n:

```powershell
cd integration-n8n
.\deploy-local.ps1
```

Or:

```powershell
cd integration-n8n
npm run deploy:local
```

Options: `-RunTests` (run unit tests before deploy), `-SkipPull` (skip `docker compose pull`).

The same script is also available as `docker\start.ps1` for backward compatibility.

Open **http://localhost:5678**, create an owner account, then add nodes — search **Tollara**.

Workflow data persists in the `n8n_data` Docker volume. The Tollara package is loaded from your repo via bind mount, not from npm.

Stop: `docker compose down` (from the `docker` folder).

### Troubleshooting: broken Tollara nodes after import

If Tollara nodes appear in the node picker but imported workflows show **“Install this node to use it”**:

1. Run **`.\deploy-local.ps1`** (not just `npm run build`) — this rebuilds, restarts n8n, and syncs the community-node registry.
2. **Delete** the broken workflow and **import again** from `example-workflows/`. n8n strips node parameters when the package failed to load on the first import.
3. Replace **`YOUR_SERVICE_SECRET`** (and **`YOUR_SERVICE_ID`** on Validate Key) on each Tollara node. Enable **Set API Endpoints** only if you use custom API URLs.

Cause: the package must expose a root **`index.js`** (referenced by `package.json` `"main"`). Without it, n8n lists nodes in Settings but does not load them at runtime.

### Troubleshooting: red exclamation on Tollara nodes

Tollara nodes do **not** use n8n credentials (v0.0.16+). If you still see **Set Credential**, **Unnamed credential**, or a warning until you create a credential, your workflow has stale data from an older package version.

**Fix:**

1. Run **`.\deploy-local.ps1`** to load **v0.0.16+**
2. **Delete** the workflow and re-import from `example-workflows/` (do not import over an existing copy)
3. Set **Service Secret** on each Tollara node
4. You can **delete** any old **Tollara Environment** credentials from Settings → Credentials — they are no longer used
5. Hard-refresh the browser (Ctrl+F5)

If only one node is affected: delete it on the canvas, drag a fresh Tollara node from the picker, reconnect wires, and paste parameters back.

If one node is still broken after that: delete it on the canvas, drag a fresh **Tollara …** node from the node picker (same name), reconnect wires, and paste parameters from the example JSON.

You do **not** need to delete the Docker image. Only reset the `n8n_data` volume if you want a completely fresh n8n instance (that wipes users and all workflows).
