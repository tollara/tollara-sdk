# SDK HTTP API dependencies (checklist)

Use this list when changing AgentVend Gateway, Core, or Usage HTTP APIs so corresponding SDK releases stay in sync.

**Scope:** HTTP clients in this repository under `sdk-java`, `sdk-js`, `sdk-python`, `sdk-dotnet`, and `sdk-rust` (HTTP feature). `sdk-php` is verifier-only (no outbound calls). Path prefixes (`/api/v1` vs `/core/api/v1`, `/api/usage` vs `/usage/api/v1`, `/api` vs `/gateway/api/v1`) are configurable; full URL shapes are in [sdk-api-spec.md](sdk-api-spec.md).

| # | Service | Method | Path (after configured base + prefix) | Role |
|---|---------|--------|----------------------------------------|------|
| 1 | Core | `POST` | `/agent-keys/validate` | Validate agent key; JSON body `agentKey`, optional `agentId`, `agentSecret`. Response body + `X-AgentVend-Timestamp` HMAC-verified with `agentSecret`. |
| 2 | Core | `POST` | `/agent-keys/estimate-usage` | Usage pre-flight; body includes `agentKey`, optional `agentId`/`agentSecret`, `estimatedUnits`. HMAC on response when signature headers present; statuses **200 / 403 / 429** handled. |
| 3 | Usage | `POST` | `/report` (default prefix `/api/usage` → `/api/usage/report`) | Signed usage report; body fields per SDK models; `X-AgentVend-Signature` / `X-AgentVend-Timestamp` on request. |
| 4 | Usage | `POST` | **Full `progressUrl`** from platform (path shape `{usagePrefix}/progress/{requestId}` when relative to usage base) | Progress update; SDK reads `signature` + `timestamp` from query string, recomputes HMAC, sends headers. |
| 5 | Usage | `POST` | **Full `callbackUrl`** from platform (path shape `{usagePrefix}/complete/{requestId}` when relative) | Completion; same signing pattern as progress. |
| 6 | Gateway | `GET` | `/requests/{requestId}/status` | Async job status; `Authorization: Bearer {agentKey}`. |
| 7 | Gateway | `GET` | `/requests/{requestId}/result` | Async job result; same auth. |

**Not implemented in the language SDK clients above:** Gateway agent **invoke** (sync/async). Callers use that API directly or via other code; this repo’s **integration-n8n** node builds `POST|GET|PUT|DELETE` `{gatewayUrl}/api/agent/{agentId}/endpoint/{endpointId}/invoke` (and variants). If you change invoke URLs, auth, or async response shape (`requestId`, `progressUrl`, `callbackUrl`), update that integration and any caller docs—not necessarily the core SDK packages.

**Backward-compatibility hotspots:** validate/estimate JSON fields and HMAC rules ([sdk-api-spec.md](sdk-api-spec.md) §2, §4); usage report/progress/complete JSON and signing; gateway polling response bodies (opaque but status codes matter).
