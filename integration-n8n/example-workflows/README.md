# Tollara demo workflows (n8n import)

Import via n8n: **Workflow menu → Import from File**.

Requires **`n8n-nodes-tollara@3.0.0+`** installed. Use **`npm run deploy:local`** from `integration-n8n` (build + restart n8n + registry sync). **`npm run build` alone is not enough.**

**n8n import quirk:** **Tollara Verify Request** may import with empty parameters and a broken/generic icon. This is an n8n editor bug (other Tollara nodes are unaffected). After every workflow import, run **`npm run repair:workflows`**, then close and reopen the workflow tab (hard refresh if needed).

## Before you activate

1. **Production Tollara:** set **Service Secret** / **Service ID** on nodes. No n8n credential is required.

2. **Local Docker demos:** on nodes that call Tollara APIs, enable **Set API Endpoints** and set URLs for your host:
   - **Progress / Complete / Report Usage** — Usage API URL (e.g. `http://host.docker.internal:8084`)
   - **Validate Key / Estimate Usage** — Core API URL (e.g. `http://host.docker.internal:8081`)
   - **Invoke / Job Status / Job Result** — Gateway API URL (e.g. `http://host.docker.internal:8083`)

3. Replace **`YOUR_SERVICE_SECRET`** on each Tollara backend node. **Service ID** on Validate Key is optional (inferred from the service key when blank).

4. **Backend** workflows: set each Webhook **Production URL** on the Tollara listing `realUrl`.

5. **Subscriber** workflows: edit **Set Config** with your service key, `serviceId`, and `endpointId` from the listing. For **`subscriber-url-metadata-estimate`**, you also need the **service secret** (same as the seller backend) — estimate uses the core pre-flight API, not buyer-only invoke auth.

6. Enable **Raw Body** on proxied backend webhooks (already set in exports).

7. Activate **backend** workflows; run **subscriber** workflows manually (Test workflow).

## Backend workflows (seller — gateway calls n8n)

| File | Mode | Webhook path | Pair with subscriber |
|------|------|--------------|----------------------|
| `backend-url-metadata-sync.json` | Proxied sync | `url-metadata` | `subscriber-url-metadata-sync.json` |
| `backend-topic-brief-async.json` | Proxied async | `topic-brief` | `subscriber-topic-brief-async.json` |
| `backend-echo-non-proxied.json` | Non-proxied | `subscriber-echo` | (HTTP Request + Bearer — no Invoke node) |

**Topic Brief** backend uses **10s** waits between progress steps (~30s total). The paired **subscriber** workflow polls up to **four** times (10s apart, ~40s) before fetching the job result.

## Subscriber workflows (buyer — n8n calls gateway)

| File | Nodes | Targets backend |
|------|-------|-----------------|
| `subscriber-url-metadata-sync.json` | Invoke (sync) | URL Metadata |
| `subscriber-url-metadata-estimate.json` | Estimate Usage → Invoke (sync) | URL Metadata — **requires service secret** (seller); for owner self-test, not buyer-only |
| `subscriber-topic-brief-async.json` | Invoke (async) → Job Status (poll) → Job Result | Topic Brief |

## Tollara listing checklist

- **URL Metadata** — proxied, sync endpoint, POST, product per-request.
- **Topic Brief** — proxied, **async** endpoint (`isAsync` required), POST, invoke via `…/invoke/async` only.
- **Echo** — non-proxied, direct webhook URL, POST.
