# API overview (Agent Hub services used by SDKs)

Base URLs for Gateway, Core, and Usage are configurable. Path prefixes differ between local/default and ECS deployment. See [sdk-repo-project-context.md](sdk-repo-project-context.md) for the full path table.

## Services

| Service   | Role for SDKs |
|-----------|----------------|
| **Gateway** | Invoke agents (sync/async); sends HMAC-signed requests to agent backends |
| **Core**    | Agent/key/subscription data; agent key validation |
| **Usage**   | Usage report, progress, completion (async), quota |

## Endpoints

| Concern            | Service | Method | Path / behavior |
|--------------------|---------|--------|------------------|
| **Invoke (sync)**  | Gateway | POST/GET/PUT/DELETE | `{gatewayPath}/agent/{agentId}/endpoint/{endpointId}/invoke` |
| **Invoke (async)** | Gateway | POST/GET | Same path with `/invoke/async`; response has `requestId`, `progressUrl`, `callbackUrl`. Auth: `Authorization: Bearer <agentKey>` |
| **Validate agent key** | Core | POST | `{corePath}/agent-keys/validate`. Body: `{ "agentKey", "agentId", "agentSecret" }`. Response: HMAC in `X-Marketplace-Signature`, `X-Marketplace-Timestamp`; body: `valid`, `userId`, `agentId`, `plan`, `roles`, `quotaRemaining`, `subscriptionActive`. Verify response HMAC. |
| **Report usage**   | Usage | POST | `{usagePath}/report`. Body: `{ userId, agentId, unitsUsed, timestamp }`. Headers: `X-Marketplace-Signature`, `X-Marketplace-Timestamp` (signature = HMAC(body + timestamp, agentSecret)). |
| **Progress (async)** | Usage | POST | `{usagePath}/progress/{requestId}` or full `progressUrl`. Body: `{ stage, percentageComplete, errorMessage?, timestamp }`. Sign: HMAC(body + timestamp, agentSecret). |
| **Completion (async)** | Usage | POST | `{usagePath}/complete/{requestId}` or full `callbackUrl`. Body: `{ status, result?, resultUrl?, contentType?, units?, timestamp }`. Same signing. |

## Headers from gateway to agent backend

`X-Marketplace-Signature`, `X-Marketplace-Timestamp`, `X-Marketplace-User-ID`, `X-Marketplace-Plan`, `X-Marketplace-Roles`, `X-Marketplace-Quota-Remaining`, `X-Marketplace-Subscription-Active`
