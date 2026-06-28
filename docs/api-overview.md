# API overview (Tollara services used by SDKs)

## Base URLs (configuration)

**SDKs do not embed production hostnames.** You always pass the Gateway, Core, and Usage **base URLs** (and choose the correct **path prefix** for your deployment). Defaults append paths such as `/api/usage/report` for usage reporting; ECS-style layouts use different prefixes. See [docs-sdk/MAIN-SDK-API-SPEC.md](../docs-sdk/MAIN-SDK-API-SPEC.md) for the full prefix table (Gateway, Core, Usage: default vs ECS).

- **Core validate:** `{coreBaseUrl}{corePathPrefix}/service-keys/validate`
- **Usage report:** `{usageBaseUrl}{usagePathPrefix}/report`
- **Async progress/completion:** use the **full** `progressUrl` and `callbackUrl` from the async invoke response (including query parameters), not a recomputed base URL.

## Services

| Service   | Role for SDKs |
|-----------|----------------|
| **Gateway** | Invoke services (sync/async); optional job status/result polling; sends HMAC-signed requests to service backends |
| **Core**    | Service key validation; usage estimate (JWT and service-key paths) |
| **Usage**   | Usage report, progress, completion (async) |

## Endpoints

| Concern            | Service | Method | Path / behavior |
|--------------------|---------|--------|------------------|
| **Invoke (sync)**  | Gateway | POST/GET/PUT/DELETE | `{gatewayPath}/service/{serviceId}/endpoint/{endpointId}/invoke` |
| **Invoke (async)** | Gateway | POST/GET/PUT/DELETE | Same path with `/invoke/async`; response has `requestId`, `progressUrl`, `callbackUrl`. Auth: `Authorization: Bearer <serviceKey>` |
| **Job status**     | Gateway | GET | `{gatewayPath}/requests/{requestId}/status` (optional polling) |
| **Job result**     | Gateway | GET | `{gatewayPath}/requests/{requestId}/result` |
| **Validate service key** | Core | POST | `{corePath}/service-keys/validate`. Body: `{ "serviceKey", "serviceId", "serviceSecret" }`. Response: HMAC in `X-Tollara-Signature`, `X-Tollara-Timestamp`; body includes `valid`, `userId`, `serviceId`, `serviceProductId`, `subscriptionStatus`, `validationSchemaVersion: 3`. Verify response HMAC. |
| **Estimate usage (service key)** | Core | POST | `{corePath}/service-keys/estimate-usage`. Signed response; `estimateSchemaVersion: 3`; balances/caps on `breakdown` only. |
| **Report usage**   | Usage | POST | `{usagePath}/report`. Body: `{ userId, serviceId, unitsUsed, timestamp }`. Response: `reportSchemaVersion: 2` with identity + `breakdown`. Headers: `X-Tollara-Signature`, `X-Tollara-Timestamp` (signature = HMAC(body + timestamp, serviceSecret)). |
| **Progress (async)** | Usage | POST | `{usagePath}/progress/{requestId}` or full `progressUrl`. Body: `{ stage, percentageComplete, errorMessage?, timestamp }`. Sign: HMAC(body + timestamp, serviceSecret). |
| **Completion (async)** | Usage | POST | `{usagePath}/complete/{requestId}` or full `callbackUrl`. Body: `{ status, result?, resultUrl?, contentType?, units?, timestamp }`. Same signing. |

## Headers from gateway to service backend (v3)

`X-Tollara-Signature`, `X-Tollara-Timestamp`, `X-Tollara-Signing-Version: 3`, `X-Tollara-User-ID`, `X-Tollara-Service-Product-ID`, `X-Tollara-Roles` (omit when empty), `X-Tollara-Subscription-Status`, optional billing headers (`X-Tollara-Billing-Model`, `X-Tollara-Measurement-Type`, `X-Tollara-Unit-Label`).

Use `grantsAccess(subscriptionStatus)` for invoke-eligible statuses: `ACTIVE`, `TRIAL`, `CANCELLING`, `CANCELLING_PENDING`.
