# SDK HTTP API dependencies (checklist)

Use this list when changing Tollara Gateway, Core, or Usage HTTP APIs so corresponding SDK releases stay in sync.

**Scope:** HTTP clients in this repository under `sdk-java`, `sdk-js`, `sdk-python`, `sdk-dotnet`, and `sdk-rust` (HTTP feature). `sdk-php` is verifier-only (no outbound calls). Path prefixes (`/api/v1` vs `/core/api/v1`, `/api/usage` vs `/usage/api/v1`, `/api` vs `/gateway/api/v1`) are configurable; full URL shapes are in [sdk-api-spec.md](sdk-api-spec.md).

| # | Service | Method | Path (after configured base + prefix) | Role |
|---|---------|--------|----------------------------------------|------|
| 1 | Core | `POST` | `/service-keys/validate` | Validate service key; JSON body `serviceKey`, optional `serviceId`, `serviceSecret`. Response body + `X-Tollara-Timestamp` HMAC-verified with `serviceSecret`. Success uses **`validationSchemaVersion: 3`** (`serviceProductId`, `subscriptionStatus`; no `plan` / `quotaRemaining` / `subscriptionActive`). |
| 2 | Core | `POST` | `/service-keys/estimate-usage` | Usage pre-flight; body includes `serviceKey`, optional `serviceId`/`serviceSecret`, `estimatedUnits`. Response uses **`estimateSchemaVersion: 3`**; balances/caps on **`breakdown`** only. HMAC on response when signature headers present; statuses **200 / 403 / 429** handled. |
| 3 | Usage | `POST` | `/report` (default prefix `/api/usage` → `/api/usage/report`) | Signed usage report; body fields per SDK models; `X-Tollara-Signature` / `X-Tollara-Timestamp` on request. |
| 4 | Usage | `POST` | **Full `progressUrl`** from platform (path shape `{usagePrefix}/progress/{requestId}` when relative to usage base) | Progress update; SDK reads `signature` + `timestamp` from query string, recomputes HMAC, sends headers. |
| 5 | Usage | `POST` | **Full `callbackUrl`** from platform (path shape `{usagePrefix}/complete/{requestId}` when relative) | Completion; same signing pattern as progress. |
| 6 | Gateway | `GET` | `/requests/{requestId}/status` | Async job status; `Authorization: Bearer {serviceKey}`. |
| 7 | Gateway | `GET` | `/requests/{requestId}/result` | Async job result; same auth. |
| 8 | Gateway | `GET`/`POST`/`PUT`/`DELETE` | `/service/{serviceId}/endpoint/{endpointId}/invoke` and `…/invoke/async` | Caller invoke; Bearer `serviceKey`; async **202** body includes `requestId`, `callbackUrl`, `progressUrl` (camelCase). |
| 9 | Core | `POST` | `/billing/usage/estimate` | JWT usage pre-flight; `Authorization: Bearer {jwt}`; body `userId`, `agentId`, `estimatedUnits`. **Not** HMAC-signed. |

**Also in repo:** **integration-n8n** may call invoke with a fixed URL layout; keep it aligned with the gateway prefix table in the canonical spec.

**Backward-compatibility hotspots:** validate/estimate v3 JSON fields and HMAC rules ([docs-sdk/MAIN-SDK-API-SPEC.md](../docs-sdk/MAIN-SDK-API-SPEC.md) §2, §4); usage report v2 JSON and signing; gateway HMAC v3 user-context string; gateway polling response bodies (opaque but status codes matter).
