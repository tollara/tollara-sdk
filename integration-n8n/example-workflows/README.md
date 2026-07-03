# Tollara demo workflows (n8n import)

Import via n8n: **Workflow menu → Import from File**.

Requires **`n8n-nodes-tollara`** installed via n8n Community Nodes.

These files ship **inside the npm package** at `n8n-nodes-tollara/example-workflows/` (for example `~/.n8n/nodes/node_modules/n8n-nodes-tollara/example-workflows/` on self-hosted n8n).

## Before you activate

1. Set **Service Secret** (and **Service Key** where needed) on each Tollara node, or configure a **Tollara API** credential and select it on the node.
2. Replace **`YOUR_*`** placeholders in subscriber **Set Config** nodes (see [Subscriber config](#subscriber-config-set-config) below).
3. **Backend** workflows: set each Webhook **Production URL** on your Tollara listing.
4. Enable **Raw Body** on proxied backend webhooks (already set in the exports).

## Backend workflows (seller — n8n receives gateway traffic)

Optional demos when n8n is the **service backend** behind a Tollara listing.

| File | Mode | Webhook path |
|------|------|--------------|
| `backend-url-metadata-sync.json` | Proxied sync | `url-metadata` |
| `backend-topic-brief-async.json` | Proxied async | `topic-brief` |
| `backend-echo-non-proxied.json` | Non-proxied | `subscriber-echo` |

Set **Service Secret** on every Tollara backend node after import.

## Subscriber workflows (buyer — invoke a listed service)

Use these when your workflow **calls** another party's Tollara listing.

| File | Pattern |
|------|---------|
| `subscriber-proxied-sync-agent.json` | Sync invoke via gateway |
| `subscriber-proxied-sync-agent-estimate.json` | Estimate usage, then sync invoke |
| `subscriber-proxied-async-agent.json` | Async invoke → poll status → fetch result |
| `subscriber-non-proxied-sync-agent.json` | Direct HTTP POST with Bearer service key |

## Subscriber config (Set Config)

| Placeholder | Used in | Where to get it |
|-------------|---------|-----------------|
| `YOUR_SERVICE_KEY` | All subscriber workflows | **Service Keys** in the Tollara UI (buyer key for the listed service) |
| `YOUR_SERVICE_ID` | Proxied sync / async | Service detail page → **Service ID** (UUID) |
| `YOUR_PROXIED_SYNC_ENDPOINT_ID` | Proxied sync (+ estimate) | Listing endpoint → **Endpoint ID** for sync POST endpoint |
| `YOUR_PROXIED_ASYNC_ENDPOINT_ID` | Proxied async | Listing endpoint → **Endpoint ID** for async POST endpoint |
| `YOUR_SERVICE_SECRET` | Estimate workflow only | Your listing's service secret |
| `agentUrl` | Non-proxied only | Direct agent URL exposed on the listing |

## Error paths

Backend webhooks: **Denied** / **Error** items include `tollaraHttpStatus` (401 / 403 / 503) for **Respond to Webhook**. Subscriber workflows branch on `tollaraOk` / `wouldAllow` → **Format Error**.

**Auth nodes:** **Tollara Verify Request** — **Allowed** / **Denied**. **Tollara Validate Key** — **Allowed** / **Denied** / **Error**.

## More help

- Node reference and install steps: [integration-n8n README on GitHub](https://github.com/tollara/tollara-sdk/tree/master/integration-n8n)
- Tollara product: [tollara.ai](https://tollara.ai)
