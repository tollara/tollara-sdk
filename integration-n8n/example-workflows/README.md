# Tollara demo workflows (n8n import)

Import via n8n: **Workflow menu → Import from File**.

Requires **`n8n-nodes-tollara@3.3.1+`** installed. Use **`npm run deploy:local`** from `integration-n8n` (build + restart n8n + registry sync). **`npm run build` alone is not enough.**

## Error paths (v3.3.0+)

Backend webhooks: **Denied** / **Error** items include `tollaraHttpStatus` (401 / 403 / 503) for **Respond to Webhook**. Subscriber workflows branch on `tollaraOk` / `wouldAllow` → **Format Error**.

**Auth nodes (v4):** **Tollara Verify Request** — **Allowed** / **Denied**. **Tollara Validate Key** — **Allowed** / **Denied** / **Error**.

**n8n import quirk:** **Tollara Verify Request** may import with empty parameters and a broken/generic icon. This is an n8n editor bug (other Tollara nodes are unaffected). After every workflow import, run **`npm run repair:workflows`**, then close and reopen the workflow tab (hard refresh if needed).

## Before you activate

1. **Production Tollara:** set **Service Secret** / **Service ID** on nodes. No n8n credential is required.

2. **Local Docker demos:** on nodes that call Tollara APIs, enable **Set API Endpoints** and set URLs for your host:
   - **Progress / Complete / Report Usage** — Usage API URL (e.g. `http://host.docker.internal:8084`)
   - **Validate Key / Estimate Usage** — Core API URL (e.g. `http://host.docker.internal:8081`)
   - **Invoke / Job Status / Job Result** — Gateway API URL (e.g. `http://host.docker.internal:8083`)

3. Replace **`YOUR_SERVICE_SECRET`** on each Tollara backend node. **Service ID** on Validate Key is optional (inferred from the service key when blank).

4. **Backend** workflows: set each Webhook **Production URL** on the Tollara listing `realUrl`.

5. **Subscriber** workflows: edit **Set Config** (see [Subscriber config](#subscriber-config-set-config) below).

6. Enable **Raw Body** on proxied backend webhooks (already set in exports).

7. **Backend** n8n workflows are optional demos. **Subscriber** workflows target Docker **agents** via listings — run subscribers manually (Test workflow); activate backends only if you use n8n as the seller service.

## Backend workflows (optional — seller n8n webhooks)

| File | Mode | Webhook path |
|------|------|--------------|
| `backend-url-metadata-sync.json` | Proxied sync | `url-metadata` |
| `backend-topic-brief-async.json` | Proxied async | `topic-brief` |
| `backend-echo-non-proxied.json` | Non-proxied | `subscriber-echo` |

## Subscriber workflows (buyer — invoke Docker agents)

Targets **proxied-agent** and **non-proxied-agent** (`agents/` in agent-hub) via Tollara listings (or direct HTTP for non-proxied). Start agents with `docker/docker-compose.e2e-agents.yml --profile local` from agent-hub.

| File | Pattern | Agent backend `realUrl` |
|------|---------|-------------------------|
| `subscriber-proxied-sync-agent.json` | Invoke (sync) | `http://host.docker.internal:9090/api/test/sync` |
| `subscriber-proxied-sync-agent-estimate.json` | Estimate → Invoke (sync) | same |
| `subscriber-proxied-async-agent.json` | Invoke (async) → poll → result | `http://host.docker.internal:9090/api/test/async` |
| `subscriber-non-proxied-sync-agent.json` | HTTP POST + Bearer (direct) | `http://host.docker.internal:9091/api/test/sync` |

## Subscriber config (Set Config)

| Placeholder | Used in | Where to get it |
|-------------|---------|-----------------|
| `YOUR_SERVICE_KEY` | All subscriber workflows | **Service Keys** in Tollara UI (buyer key for the listed service) |
| `YOUR_SERVICE_ID` | Proxied sync / async | Service detail page → **Service ID** (UUID) |
| `YOUR_PROXIED_SYNC_ENDPOINT_ID` | Proxied sync (+ estimate) | Listing endpoint → **Endpoint ID** for sync POST endpoint |
| `YOUR_PROXIED_ASYNC_ENDPOINT_ID` | Proxied async | Listing endpoint → **Endpoint ID** for async POST endpoint (`isAsync`) |
| `YOUR_SERVICE_SECRET` | Estimate workflow only | Same as listing / agent `AGENT_SECRET` |
| `agentUrl` | Non-proxied only | Direct agent URL (default in workflow; change if not using Docker local profile) |

**Listing secrets:** agent `AGENT_SECRET` (default `test-service-secret-key-change-in-production` in e2e compose) must match the **service secret** on the Tollara listing.

**Proxied listings:** gateway must reach agent URLs (`host.docker.internal` from gateway container).

**Non-proxied:** no gateway invoke — only `YOUR_SERVICE_KEY` and `agentUrl` in Set Config.

## Tollara listing checklist (agents)

- **Proxied sync** — proxied, sync, POST, `realUrl` → proxied-agent `/api/test/sync`.
- **Proxied async** — proxied, **`isAsync`**, POST, `realUrl` → proxied-agent `/api/test/async`.
- **Non-proxied** — direct URL exposed, `realUrl` → non-proxied-agent `/api/test/sync`.
