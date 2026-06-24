# Tollara demo workflows (n8n import)

Import via n8n: **Workflow menu → Import from File**.

Requires **`n8n-nodes-tollara`** installed (`integration-n8n` built and loaded in n8n).

## Before you activate

1. Create credentials (Settings → Credentials):
   - **Tollara Proxied API** — service secret from proxied backend(s); optional Gateway `http://host.docker.internal:8083`; optional Usage `http://host.docker.internal:8084` for Progress/Complete URL rewrite.
   - **Tollara Non-proxied API** — service secret + service ID; Core `http://host.docker.internal:8081`, Usage `http://host.docker.internal:8084`.
   - **Tollara Subscriber API** — for caller workflows: Gateway `http://host.docker.internal:8083`, Core `http://host.docker.internal:8081` (for Estimate Usage), service secret (required by credential; use your org secret).
2. After import, assign credentials on Tollara nodes (imports reference credential **names** only).
3. **Backend** workflows: set each Webhook **Production URL** on the Tollara listing `realUrl` → `http://host.docker.internal:5678/webhook/<path>`.
4. **Subscriber** workflows: edit **Set Config** with your service key, `serviceId`, and `endpointId` from the listing.
5. Enable **Raw Body** on proxied backend webhooks (already set in exports).
6. Activate **backend** workflows; run **subscriber** workflows manually (Test workflow).

## Backend workflows (seller — gateway calls n8n)

| File | Mode | Webhook path | Pair with subscriber |
|------|------|--------------|----------------------|
| `backend-url-metadata-sync.json` | Proxied sync | `url-metadata` | `subscriber-url-metadata-sync.json` |
| `backend-topic-brief-async.json` | Proxied async | `topic-brief` | `subscriber-topic-brief-async.json` |
| `backend-echo-non-proxied.json` | Non-proxied | `subscriber-echo` | (HTTP Request + Bearer — no Invoke node) |

**Topic Brief** uses **10s** waits between progress steps (~30s total) so the Activity UI shows visible polling.

## Subscriber workflows (buyer — n8n calls gateway)

| File | Nodes | Targets backend |
|------|-------|-----------------|
| `subscriber-url-metadata-sync.json` | Invoke (sync) | URL Metadata |
| `subscriber-url-metadata-estimate.json` | Estimate Usage → Invoke (sync) | URL Metadata |
| `subscriber-topic-brief-async.json` | Invoke (async) → Job Status (poll) → Job Result | Topic Brief |

## Tollara listing checklist

- **URL Metadata** — proxied, sync endpoint, POST, product per-request.
- **Topic Brief** — proxied, **async** endpoint (`isAsync`), POST.
- **Echo** — non-proxied, direct webhook URL, POST.
