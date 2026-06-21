# Tollara demo workflows (n8n import)

Import via n8n: **Workflow menu → Import from File**.

Requires **`n8n-nodes-tollara`** installed (`integration-n8n` built and loaded in n8n).

## Before you activate

1. Create credentials (Settings → Credentials):
   - **Tollara Proxied API** — service secret from proxied service(s); optional Gateway API URL `http://host.docker.internal:8083` for local Docker.
   - **Tollara Non-proxied API** — service secret + service ID; Core `http://host.docker.internal:8081`, Usage `http://host.docker.internal:8084` for local Docker.
2. After import, open each workflow and assign credentials on Tollara nodes (imports reference credential **names** only).
3. On each **Webhook** node, confirm **Production URL** and set Tollara service `realUrl` to `http://host.docker.internal:5678/webhook/<path>` (Docker Tollara stack).
4. Enable **Raw Body** on proxied webhooks (already set in these exports).
5. Activate workflows and register matching Tollara listings (see workflow sticky notes / names).

## Workflows

| File | Tollara mode | Webhook path | Demo invoke |
|------|--------------|--------------|-------------|
| `url-metadata-sync.json` | Proxied sync | `url-metadata` | `POST …/invoke` with `{"url":"https://example.com"}` |
| `topic-brief-async-backend.json` | Proxied async | `topic-brief` | `POST …/invoke/async` with `{"topic":"Docker"}` |
| `subscriber-echo-non-proxied.json` | Non-proxied | `subscriber-echo` | `POST` webhook URL with `Authorization: Bearer <key>` |

## Tollara listing checklist

- **URL Metadata** — proxied, sync endpoint, POST, product per-request.
- **Topic Brief** — proxied, **async** endpoint (`isAsync`), POST, product per-request or async-friendly plan.
- **Subscriber Echo** — non-proxied, expose direct URL, POST, product per-request; Report Usage node bills after response.
