# SDK HTTP API dependencies (checklist)

Use this list when changing Tollara Gateway, Core, or Usage HTTP APIs so corresponding SDK releases stay in sync.

**Scope:** HTTP clients in this repository under `sdk-java`, `sdk-js`, `sdk-python`, `sdk-dotnet`, and `sdk-rust` (HTTP feature). `sdk-php` is verifier-only (no outbound calls). Path prefixes (`/api/v1` vs `/core/api/v1`, `/api/usage` vs `/usage/api/v1`, `/api` vs `/gateway/api/v1`) are configurable; full URL shapes are in [sdk-api-spec.md](sdk-api-spec.md).

| # | Service | Method | Path (after configured base + prefix) | Role |
|---|---------|--------|----------------------------------------|------|
| 1 | Core | `POST` | `/agent-keys/validate` | Validate service key; JSON body `serviceKey`, optional `serviceId`, `serviceSecret`. Response body + `X-Tollara-Timestamp` HMAC-verified with `serviceSecret`. |
| 2 | Core | `POST` | `/agent-keys/estimate-usage` | Usage pre-flight; body includes `serviceKey`, optional `serviceId`/`serviceSecret`, `estimatedUnits`. HMAC on response when signature headers present; statuses **200 / 403 / 429** handled. |
| 3 | Usage | `POST` | `/report` (default prefix `/api/usage` → `/api/usage/report`) | Signed usage report; body fields per SDK models; `X-Tollara-Signature` / `X-Tollara-Timestamp` on request. |
| 4 | Usage | `POST` | **Full `progressUrl`** from platform (path shape `{usagePrefix}/progress/{requestId}` when relative to usage base) | Progress update; SDK reads `signature` + `timestamp` from query string, recomputes HMAC, sends headers. |
| 5 | Usage | `POST` | **Full `callbackUrl`** from platform (path shape `{usagePrefix}/complete/{requestId}` when relative) | Completion; same signing pattern as progress. |
| 6 | Gateway | `GET` | `/requests/{requestId}/status` | Async job status; `Authorization: Bearer {serviceKey}`. |
| 7 | Gateway | `GET` | `/requests/{requestId}/result` | Async job result; same auth. |
| 8 | Gateway | `GET`/`POST`/`PUT`/`DELETE` | `/service/{serviceId}/endpoint/{endpointId}/invoke` and `…/invoke/async` | Caller invoke; Bearer `serviceKey`; async **202** body includes `requestId`, `callbackUrl`, `progressUrl` (camelCase). |
| 9 | Core | `POST` | `/billing/usage/estimate` | JWT usage pre-flight; `Authorization: Bearer {jwt}`; body `userId`, `agentId`, `estimatedUnits`. **Not** HMAC-signed. |

**Also in repo:** **integration-n8n** may call invoke with a fixed URL layout; keep it aligned with the gateway prefix table in the canonical spec.

**Backward-compatibility hotspots:** validate/estimate JSON fields and HMAC rules ([sdk-api-spec.md](sdk-api-spec.md) §2, §4); usage report/progress/complete JSON and signing; gateway polling response bodies (opaque but status codes matter).
