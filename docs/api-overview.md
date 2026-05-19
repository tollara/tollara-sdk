# API overview (Tollara services used by SDKs)

## Base URLs (configuration)

**SDKs do not embed production hostnames.** You always pass the Gateway, Core, and Usage **base URLs** (and choose the correct **path prefix** for your deployment). Defaults append paths such as `/api/usage/report` for usage reporting; ECS-style layouts use different prefixes. See [sdk-api-spec.md](sdk-api-spec.md) for the full prefix table (Gateway, Core, Usage: default vs ECS).

- **Core validate:** `{coreBaseUrl}{corePathPrefix}/agent-keys/validate` (you typically pass `coreBaseUrl` already including `corePathPrefix`, e.g. `https://core.example.com/api/v1`).
- **Usage report:** `{usageBaseUrl}/api/usage/report` in the reference SDKs when `usageBaseUrl` is the service host only; for ECS use a base that includes `/usage/api/v1` per spec.
- **Async progress/completion:** use the **full** `progressUrl` and `callbackUrl` from the async invoke response (including query parameters), not a recomputed base URL.

## Services

| Service   | Role for SDKs |
|-----------|----------------|
| **Gateway** | Invoke agents (sync/async); optional job status/result polling; sends HMAC-signed requests to agent backends |
| **Core**    | Agent/key/subscription data; agent key validation |
| **Usage**   | Usage report, progress, completion (async), quota |

## Endpoints

| Concern            | Service | Method | Path / behavior |
|--------------------|---------|--------|------------------|
| **Invoke (sync)**  | Gateway | POST/GET/PUT/DELETE | `{gatewayPath}/agent/{agentId}/endpoint/{endpointId}/invoke` |
| **Invoke (async)** | Gateway | POST/GET | Same path with `/invoke/async`; response has `requestId`, `progressUrl`, `callbackUrl`. Auth: `Authorization: Bearer <agentKey>` |
| **Job status**     | Gateway | GET | `{gatewayPath}/requests/{requestId}/status` (optional polling) |
| **Job result**     | Gateway | GET | `{gatewayPath}/requests/{requestId}/result` |
| **Validate agent key** | Core | POST | `{corePath}/agent-keys/validate`. Body: `{ "agentKey", "agentId", "agentSecret" }`. Response: HMAC in `X-Tollara-Signature`, `X-Tollara-Timestamp`; body: `valid`, `userId`, `agentId`, `plan`, `roles`, `quotaRemaining`, `subscriptionActive`. Verify response HMAC. |
| **Report usage**   | Usage | POST | `{usagePath}/report`. Body: `{ userId, agentId, unitsUsed, timestamp }`. Headers: `X-Tollara-Signature`, `X-Tollara-Timestamp` (signature = HMAC(body + timestamp, agentSecret)). |
| **Progress (async)** | Usage | POST | `{usagePath}/progress/{requestId}` or full `progressUrl`. Body: `{ stage, percentageComplete, errorMessage?, timestamp }`. Sign: HMAC(body + timestamp, agentSecret). |
| **Completion (async)** | Usage | POST | `{usagePath}/complete/{requestId}` or full `callbackUrl`. Body: `{ status, result?, resultUrl?, contentType?, units?, timestamp }`. Same signing. |

## Headers from gateway to agent backend

`X-Tollara-Signature`, `X-Tollara-Timestamp`, `X-Tollara-User-ID`, `X-Tollara-Plan`, `X-Tollara-Roles`, `X-Tollara-Quota-Remaining`, `X-Tollara-Subscription-Active`
