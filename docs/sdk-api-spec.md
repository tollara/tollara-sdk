# AgentVend SDK – API specification

This document specifies the exact HTTP APIs that the AgentVend SDK calls. Use it in the **agentvend-sdk** project to implement clients in any language. Base URLs for each service are configurable; path prefixes depend on deployment (default vs ECS).

**Services and path prefixes:**

| Service | Default path prefix | ECS path prefix |
|--------|----------------------|------------------|
| Gateway | `/api` (controller path) | `/gateway/api/v1` |
| Core | `/api/v1` (servlet context) | `/core/api/v1` |
| Usage | `/api/usage` (controller under `/`) | `/usage/api/v1` (controller path empty) |

Full URL = `{baseUrl}{pathPrefix}{path}`. Example: Core validate = `{coreBaseUrl}/api/v1/agent-keys/validate` (default) or `{coreBaseUrl}/core/api/v1/agent-keys/validate` (ECS).

---

## 1. Gateway – Invoke agent (caller)

The SDK uses these when acting as a **caller** (invoking an agent).

### 1.1 Sync invoke

**Request**

- **Method:** `GET` | `POST` | `PUT` | `DELETE`
- **URL:** `{gatewayBaseUrl}{gatewayPathPrefix}/agent/{agentId}/endpoint/{endpointId}/invoke`
  - `gatewayPathPrefix`: `/api` (default) or `/gateway/api/v1` (ECS)
- **Headers:**
  - `Authorization: Bearer {agentKey}` (required)
  - `Content-Type: application/json` (for POST/PUT with body)
- **Body:** Optional. For POST/PUT, any JSON or raw body; forwarded to the agent backend.
- **Query:** Optional query params forwarded to the backend.

**Response**

- Status: same as the agent backend response (e.g. 200, 4xx, 5xx).
- Body: agent backend response body (opaque to SDK; pass through to caller).

### 1.2 Async invoke

**Request**

- **Method:** `GET` | `POST` | `PUT` | `DELETE`
- **URL:** `{gatewayBaseUrl}{gatewayPathPrefix}/agent/{agentId}/endpoint/{endpointId}/invoke/async`
- **Headers:** Same as sync (`Authorization: Bearer {agentKey}`, optional `Content-Type`, etc.).
- **Body:** Optional; same as sync for POST/PUT.

**Response**

- **Status:** `202 Accepted` on success.
- **Body (JSON):**

| Field | Type | Description |
|-------|------|-------------|
| `status` | string | e.g. `"ACCEPTED"` |
| `requestId` | string | Unique ID for this async job. Use for progress/complete and status/result. |
| `callbackUrl` | string | URL to POST completion (usage service). Agent backend must POST completion here. |
| `progressUrl` | string | URL for **caller** to poll job status (gateway). Agent backend may receive a different progress URL in the request body for posting progress. |

**Note for agent backend:** When the gateway forwards an async request to the agent, the **body** sent to the agent may include `request_id`, `progress_url`, and `callback_url`. The agent should POST progress to `progress_url` and completion to `callback_url` (these point to the usage service). If the agent does not receive these, it may build them as `{usageBaseUrl}{usagePathPrefix}/progress/{requestId}` and `{usageBaseUrl}{usagePathPrefix}/complete/{requestId}`.

### 1.3 Optional: Job status (caller polling)

- **Method:** `GET`
- **URL:** `{gatewayBaseUrl}{gatewayPathPrefix}/requests/{requestId}/status`
- **Headers:** `Authorization: Bearer {agentKey}` (if required by deployment).

**Response:** JSON with job status (e.g. `PENDING`, `COMPLETED`, `FAILED`). Structure is deployment-specific.

### 1.4 Optional: Job result (caller polling)

- **Method:** `GET`
- **URL:** `{gatewayBaseUrl}{gatewayPathPrefix}/requests/{requestId}/result`
- **Headers:** `Authorization: Bearer {agentKey}` (if required).

**Response:** JSON with result when job is completed.

---

## 2. Core – Validate agent key

Used by both callers and backends to validate an agent key and get user/plan/quota. Response is HMAC-signed; the SDK **must** verify the signature.

### 2.1 Validate agent key

**Request**

- **Method:** `POST`
- **URL:** `{coreBaseUrl}{corePathPrefix}/agent-keys/validate`
  - `corePathPrefix`: `/api/v1` (default) or `/core/api/v1` (ECS)
- **Headers:** `Content-Type: application/json`
- **Body (JSON):**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `agentKey` | string | yes | The agent API key (Bearer value). |
| `agentId` | string (UUID) | no | Agent ID. If omitted, core may look it up from the key. |
| `agentSecret` | string | no* | Agent secret. *Required for HMAC verification of the response; client must have it to verify. |

**Response (success)**

- **Status:** `200 OK`
- **Headers:**
  - `X-AgentVend-Signature`: HMAC-SHA256 (Base64) of `responseBody + timestamp` (timestamp as string, no separator), key = `agentSecret`.
  - `X-AgentVend-Timestamp`: Numeric string (Unix epoch seconds).
- **Body (JSON):**

| Field | Type | Description |
|-------|------|-------------|
| `valid` | boolean | `true` if key is valid. |
| `userId` | string | External user ID. |
| `agentId` | string | Agent UUID. |
| `plan` | string | Plan/tier (e.g. `"basic"`, `"owner"`). |
| `roles` | string[] | User roles (may be empty). |
| `quotaRemaining` | number | Remaining quota (e.g. requests or units). |
| `subscriptionActive` | boolean | Whether subscription is active. |
| `timestamp` | number | Unix epoch seconds (same as header). |
| `error` | string | Present only on error (e.g. invalid key). |

**Response (error)**

- **Status:** `401 Unauthorized` with body `valid: false` and `error` message. Response may still include HMAC headers; verify if present.

**SDK must:**

1. Compute `canonical = responseBodyJsonString + X-AgentVend-Timestamp` (string concatenation, no separator).
2. Compute `expectedSignature = Base64(HMAC-SHA256(canonical, agentSecret))`.
3. Compare `expectedSignature` with `X-AgentVend-Signature` using **constant-time** comparison.
4. If they differ, treat the response as invalid and do not trust the body.

---

## 3. Usage service – Report, progress, completion

All three endpoints require request body + timestamp signed with **agent secret** (not the agent key). Algorithm: `canonical = bodyJsonString + timestamp` (timestamp = numeric string, same as header), then `signature = Base64(HMAC-SHA256(canonical, agentSecret))`.

**Common headers for all three:**

- `Content-Type: application/json`
- `X-AgentVend-Signature`: signature as above
- `X-AgentVend-Timestamp`: numeric string (Unix epoch seconds)

### 3.1 Report usage (non-proxied agents)

**Request**

- **Method:** `POST`
- **URL:** `{usageBaseUrl}{usagePathPrefix}/report`
  - `usagePathPrefix`: `/api/usage` (default) or `/usage/api/v1` (ECS)
- **Headers:** As above (signature = HMAC(body + timestamp, agentSecret)).
- **Body (JSON):**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `userId` | string | yes | External user ID. |
| `agentId` | string (UUID) | yes | Agent ID. |
| `unitsUsed` | number | yes | Units consumed (e.g. requests, tokens). |
| `timestamp` | string or number | no | ISO-8601 or epoch; optional for signing (header timestamp used for HMAC). |

**Response**

- **Status:** `200 OK`
- **Body (JSON):**

| Field | Type | Description |
|-------|------|-------------|
| `status` | string | e.g. `"ok"` |
| `warning` | string | Optional warning message. |
| `isOverLimit` | boolean | `true` if over limit / spending cap exceeded. |
| `remainingRequestsPerPeriod` | number | Remaining requests in period (if applicable). |
| `remainingTimeUnitsPerPeriod` | number | Remaining time units (if applicable). |
| `remainingSpendingCap` | number | Remaining spending cap (if applicable). |
| `overageRate` | number | Overage rate (if applicable). |

### 3.2 Progress (async jobs)

**Request**

- **Method:** `POST`
- **URL:** `{usageBaseUrl}{usagePathPrefix}/progress/{requestId}`  
  Or use the full `progress_url` from the async request body sent to the agent (if present).
- **Headers:** Same as in §3 (signature = HMAC(body + timestamp, agentSecret)).
- **Body (JSON):**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `stage` | string | yes | Progress stage name. |
| `percentageComplete` | number (int) | yes | 0–100. |
| `errorMessage` | string | no | Error message if failed. |
| `timestamp` | string or number | no | Optional; header timestamp used for HMAC. |

**Response**

- **Status:** `200 OK`
- **Body:** Empty or implementation-specific.

### 3.3 Completion (async jobs)

**Request**

- **Method:** `POST`
- **URL:** `{usageBaseUrl}{usagePathPrefix}/complete/{requestId}`  
  Or use the full `callback_url` from the async response or from the request body sent to the agent.
- **Headers:** Same as in §3 (signature = HMAC(body + timestamp, agentSecret)).
- **Body (JSON):**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `status` | string | yes | e.g. `"COMPLETED"`, `"FAILED"`. |
| `result` | string | no | Inline result (e.g. text). |
| `resultUrl` | string | no | URL to result (e.g. file). |
| `contentType` | string | no | Content type of result. |
| `units` | number | no | Units used (for billing); recommended when applicable. |
| `timestamp` | string or number | no | Optional; header timestamp used for HMAC. |

**Response**

- **Status:** `200 OK`
- **Body:** Empty or implementation-specific.

---

## 4. Headers sent from gateway to agent backend (inbound)

When the SDK is used in an **agent backend** to verify incoming requests from the gateway, the gateway sends (among others):

| Header | Description |
|--------|-------------|
| `X-AgentVend-Signature` | HMAC of `payload + timestamp + userContextString` (see HMAC spec in SDK repo). |
| `X-AgentVend-Timestamp` | Numeric string. |
| `X-AgentVend-User-ID` | User ID. |
| `X-AgentVend-Plan` | Plan name. |
| `X-AgentVend-Roles` | Comma-separated roles. |
| `X-AgentVend-Quota-Remaining` | Remaining quota. |
| `X-AgentVend-Subscription-Active` | `"true"` / `"false"`. |

Verification: canonical string = raw request body (string) + timestamp + userContextString, where userContextString = userId + plan + roles.join(',') + quotaRemaining (no separators; null/empty as ""). Signature = Base64(HMAC-SHA256(canonical, agentSecret)). Use constant-time compare. Full HMAC spec and test vectors belong in the SDK repo (e.g. `docs/hmac-spec.md`).

---

## 5. Summary table (SDK calls)

| SDK capability | Service | Method | Path (relative to path prefix) |
|----------------|---------|--------|----------------------------------|
| Invoke sync | Gateway | GET/POST/PUT/DELETE | `/agent/{agentId}/endpoint/{endpointId}/invoke` |
| Invoke async | Gateway | GET/POST/PUT/DELETE | `/agent/{agentId}/endpoint/{endpointId}/invoke/async` |
| Validate agent key | Core | POST | `/agent-keys/validate` |
| Report usage | Usage | POST | `/report` |
| Progress | Usage | POST | `/progress/{requestId}` |
| Completion | Usage | POST | `/complete/{requestId}` |
| Job status (optional) | Gateway | GET | `/requests/{requestId}/status` |
| Job result (optional) | Gateway | GET | `/requests/{requestId}/result` |

---

## 6. HMAC summary (outbound from SDK)

- **Algorithm:** HMAC-SHA256; key = agent secret (UTF-8); output = Base64.
- **Validate response (core → SDK):** Verify `X-AgentVend-Signature` = Base64(HMAC-SHA256(responseBody + X-AgentVend-Timestamp, agentSecret)). Constant-time compare.
- **Report / progress / completion (SDK → usage):** Send `X-AgentVend-Signature` = Base64(HMAC-SHA256(bodyJsonString + timestamp, agentSecret)) and `X-AgentVend-Timestamp` = timestamp (numeric string).

---

*Source: AgentVend platform. For use in agentvend-sdk. Path prefixes and base URLs are configurable per environment.*
