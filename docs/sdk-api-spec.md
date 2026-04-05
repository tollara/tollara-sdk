# AgentVend SDK – API specification

This document specifies the exact HTTP APIs that the AgentVend (Agent Hub) SDK calls. Use it in the **agent-hub-sdk** / AgentVend SDK project to implement clients in any language. Base URLs for each service are configurable; path prefixes depend on deployment (default vs ECS vs Docker).

**Services and path prefixes:**

| Service | Default path prefix | ECS path prefix |
|--------|----------------------|------------------|
| Gateway | `/api` (controller path) | `/gateway/api/v1` |
| Core | `/api/v1` (servlet context) | `/core/api/v1` |
| Usage | `/api/usage` (controller under `/`) | `/usage/api/v1` (controller path empty) |

Full URL = `{baseUrl}{pathPrefix}{path}`. Example: Core validate = `{coreBaseUrl}/api/v1/agent-keys/validate` (default) or `{coreBaseUrl}/core/api/v1/agent-keys/validate` (ECS).

**Docker / no servlet context-path:** Some core deployments use `context-path: /`. In that case paths are still exposed as `/api/v1/agent-keys/validate`, `/api/v1/agent-keys/estimate-usage`, etc. (single segment after the host:port base URL). Do not duplicate `/api/v1` if your configured `coreBaseUrl` already ends with it.

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

Used by both callers and backends to validate an agent key and get user/plan/entitlement snapshot. Response is HMAC-signed; the SDK **must** verify the signature. Numeric pre-flight uses **estimate** endpoints (§2.2–2.3), not `quotaRemaining` on validate.

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
| `plan` | string | Lowercase `SubscriptionPlan` name for subscribers (e.g. `"basic"`); `"owner"` for agent owner with no user product. |
| `roles` | string[] | User roles (may be empty). |
| `validationSchemaVersion` | number | **`2`** on success; indicates signed JSON shape (no `quotaRemaining` field). |
| `subscriptionActive` | boolean | Whether subscription is active. |
| `billingModelType` | string | Optional. `SUBSCRIPTION`, `USAGE_POSTPAID`, `USAGE_INSTANT`, `PREPAID`; omitted/null for owner path. |
| `measurementType` | string | Optional. e.g. `PER_REQUEST`, `PER_TIME_UNIT`, `PER_TOKEN`, `PER_BYTE`; omitted/null when not applicable. |
| `unitLabel` | string | Optional config label (e.g. `request`, `token`). |
| `timestamp` | number | Unix epoch seconds (same as header). |
| `error` | string | Present only on error (e.g. invalid key). |

**Response (error)**

- **Status:** `401 Unauthorized` with body `valid: false` and `error` message. Response may still include HMAC headers; verify if present.

**SDK must:**

1. Compute `canonical = responseBodyJsonString + X-AgentVend-Timestamp` (string concatenation, no separator).
2. Compute `expectedSignature = Base64(HMAC-SHA256(canonical, agentSecret))`.
3. Compare `expectedSignature` with `X-AgentVend-Signature` using **constant-time** comparison.
4. If they differ, treat the response as invalid and do not trust the body.

### 2.2 Usage estimate (JWT)

**Request**

- **Method:** `POST`
- **URL:** `{coreBaseUrl}{corePathPrefix}/billing/usage/estimate`
- **Headers:** `Content-Type: application/json`, `Authorization: Bearer {userOrServiceJwt}`
- **Body (JSON):**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `userId` | string (UUID) | yes | Internal core user id (not Cognito sub). |
| `agentId` | string (UUID) | yes | Agent id. |
| `estimatedUnits` | number | yes | Positive; units to pre-check. |

**Authorization:** The principal must be allowed to estimate for `userId` (typically the end user for themselves, or a service/gateway principal with appropriate role).

**Response body (JSON)** — same shape for JWT and agent-key paths (agent-key adds fields in §2.3).

| Field | Type | Description |
|-------|------|-------------|
| `sufficientCredits` | boolean | **PREPAID:** enough credits for this `estimatedUnits`. Other models: `true` when credits are not used for the check. |
| `wouldExceedCap` | boolean | **Spending cap / surplus:** `true` when this estimate would be rejected for the same reasons as usage **record** (cumulative overage cost over cap, or surplus overage units on the request when a cap applies). **USAGE_INSTANT:** spend would exceed cap when configured. |
| `wouldAllow` | boolean | `true` iff core would return **200** for this estimate (aligned with `sufficientCredits` / `wouldExceedCap` gating). |
| `estimatedCost` | number or null | Estimated charge for this chunk where applicable. |
| `remainingCredits` | number or null | **PREPAID:** balance before charge; other models often `null`. |
| `remainingSpendingCap` | number or null | Headroom under cap after this estimate when applicable. |
| `billingModelType` | string | e.g. `PREPAID`, `SUBSCRIPTION`, `USAGE_POSTPAID`, `USAGE_INSTANT`. |
| `measurementType` | string or null | e.g. `PER_REQUEST`, `PER_TIME_UNIT`, `PER_TOKEN`, `PER_BYTE`. |
| `unitLabel` | string or null | Config label (e.g. `request`). |
| `breakdown` | object or null | Calculator snapshot for **SUBSCRIPTION** / **USAGE_POSTPAID**; synthetic view for **PREPAID**; **null** for **USAGE_INSTANT**. See table below. |

**`breakdown` object** (when present): mirrors the usage cost calculation for that estimate — numeric fields such as `unitsUsed`, `baseUnitsUsed`, `overageUnits`, `chargeableOverageUnits`, `surplusOverageUnits`, `overageCost`, `totalOverageCost`, `unitsRemaining`, `remainingSpendingCap`, `totalUnitsUsedThisCycle`, plus booleans `isOverLimit`, `isOverage`, `isOverageAllowed`. Omitted-null fields may be absent.

**HTTP status**

- **200 OK** — `wouldAllow` is `true`.
- **403 Forbidden** — Two cases: (1) **Authorization:** JWT principal is not allowed to estimate for the requested internal `userId` — core may return **403 with an empty body** (no JSON). (2) **Insufficient credits (PREPAID):** core returns **403 with a JSON body** (same estimate fields as above).
- **429 Too Many Requests** — Would exceed spending cap / surplus rules; body includes the estimate JSON above.
- **400 / 500** — Billing or server errors (see core logs); response may include a **minimal** estimate-shaped body (`wouldAllow` false) or an empty body depending on failure path.

### 2.3 Usage estimate (agent key)

Same trust model as §2.1 (validate): **no Bearer**. Use when the backend only has **agent key + agent secret**.

**Request**

- **Method:** `POST`
- **URL:** `{coreBaseUrl}{corePathPrefix}/agent-keys/estimate-usage`
- **Headers:** `Content-Type: application/json` only
- **Body (JSON):**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `agentKey` | string | yes | Same as validate. |
| `agentId` | string (UUID) | no | If omitted, core may resolve from key. |
| `agentSecret` | string | no | If sent non-empty, must match server secret (same as validate). |
| `estimatedUnits` | number | yes | Positive. |

**Response (success body)**

Same business fields as §2.2 (`wouldAllow`, `breakdown`, etc.), plus:

| Field | Type | Description |
|-------|------|-------------|
| `estimateSchemaVersion` | number | Starts at **1**; bump if JSON shape changes. |
| `timestamp` | number | Unix epoch seconds (aligned with signing). |

**Headers on success:** `X-AgentVend-Signature`, `X-AgentVend-Timestamp` — verify **`HMAC-SHA256(responseBodyJson + timestamp, agentSecret)`** (same concatenation rule as validate response).

**Status codes:** **200** allowed; **403** insufficient credits; **429** cap; **400** billing rule failure; **401** bad key/secret/agent. **401** / some **400** / **500** responses may be **unsigned** and have an **empty** body (match validate error behavior). When status is **200**, **403** (credits), or **429**, the JSON body is signed if signature headers are present — verify HMAC on the **raw** body string + timestamp.

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

The gateway signs a fixed **user-context suffix** after the request payload and timestamp (**schema v2**). The suffix begins with literal **`"2"`** and does **not** include a quota segment. Verification must use `GatewayHmacUserContext` / the same field order as the gateway.

When the SDK is used in an **agent backend** to verify incoming requests from the gateway, the gateway sends (among others):

| Header | Description |
|--------|-------------|
| `X-AgentVend-Signature` | HMAC of `payload + timestamp + userContextString` (see below). |
| `X-AgentVend-Timestamp` | Numeric string (Unix epoch seconds). |
| `X-AgentVend-User-ID` | User ID. |
| `X-AgentVend-Plan` | Plan name. |
| `X-AgentVend-Roles` | Comma-separated roles. |
| `X-AgentVend-Signing-Version` | **`2`** when gateway uses HMAC user-context v2. |
| `X-AgentVend-Subscription-Active` | `"true"` / `"false"` (always sent). |
| `X-AgentVend-Billing-Model` | Optional. e.g. `SUBSCRIPTION`, `PREPAID`, `USAGE_INSTANT`, `USAGE_POSTPAID` (omitted for owner path). |
| `X-AgentVend-Measurement-Type` | Optional. e.g. `PER_REQUEST`, `PER_TOKEN`. |
| `X-AgentVend-Unit-Label` | Optional. e.g. `request`, `token`. |

**Verification (v2):** Let `payloadString` be the raw request body as a string (empty string if absent). Let `timestamp` be the long parsed from `X-AgentVend-Timestamp`. Build `userContextString` = **`"2"`** + concatenation in this **exact** order (use `""` for null/absent strings; `subscriptionActive` is always `"true"` or `"false"`):

1. `userId` (or `""`)
2. `plan` (or `""`)
3. If roles present: comma-joined roles; else nothing (no separator before next field)
4. `Boolean.toString(subscriptionActive)`
5. `billingModelType` or `""`
6. `measurementType` or `""`
7. `unitLabel` or `""`

Then `canonical = payloadString + timestamp + userContextString` (timestamp as decimal digits, no separator). `signature = Base64(HMAC-SHA256(canonical, agentSecret))`. Compare with constant-time equality to `X-AgentVend-Signature`.

Reference implementation: `com.bugisiw.marketplace.common.util.GatewayHmacUserContext` in **common-utils** (shared with gateway-service).

For **async** invokes, the gateway signs the JSON serialization of the async body **before** `progress_url` / `callback_url` query params are added (same as before).

---

## 5. Summary table (SDK calls)

| SDK capability | Service | Method | Path (relative to path prefix) |
|----------------|---------|--------|----------------------------------|
| Invoke sync | Gateway | GET/POST/PUT/DELETE | `/agent/{agentId}/endpoint/{endpointId}/invoke` |
| Invoke async | Gateway | GET/POST/PUT/DELETE | `/agent/{agentId}/endpoint/{endpointId}/invoke/async` |
| Validate agent key | Core | POST | `/agent-keys/validate` |
| Usage estimate (JWT) | Core | POST | `/billing/usage/estimate` |
| Usage estimate (agent key) | Core | POST | `/agent-keys/estimate-usage` |
| Report usage | Usage | POST | `/report` |
| Progress | Usage | POST | `/progress/{requestId}` |
| Completion | Usage | POST | `/complete/{requestId}` |
| Job status (optional) | Gateway | GET | `/requests/{requestId}/status` |
| Job result (optional) | Gateway | GET | `/requests/{requestId}/result` |

---

## 6. HMAC summary (outbound from SDK)

- **Algorithm:** HMAC-SHA256; key = agent secret (UTF-8); output = Base64.
- **Validate response (core → SDK):** Verify `X-AgentVend-Signature` = Base64(HMAC-SHA256(responseBody + X-AgentVend-Timestamp, agentSecret)). Constant-time compare. Success bodies include `validationSchemaVersion: 2` (no `quotaRemaining`).
- **Agent-key estimate response (core → SDK):** Same verification pattern as validate: `responseBody + timestamp` with `agentSecret`. Use the **raw** response body string; new fields (`wouldAllow`, `breakdown`, etc.) are included in the signed JSON automatically.
- **Gateway → agent (inbound):** `payload + timestamp + GatewayHmacUserContext` v2 (leading `"2"`, no quota).
- **Report / progress / completion (SDK → usage):** Send `X-AgentVend-Signature` = Base64(HMAC-SHA256(bodyJsonString + timestamp, agentSecret)) and `X-AgentVend-Timestamp` = timestamp (numeric string).

---

*Source: AgentVend platform (agent-hub repo). For use in agent-hub-sdk / AgentVend SDK. Path prefixes and base URLs are configurable per environment. Pair with `documentation/sdk-repo-implementation-prompt.md` for a paste-ready SDK work brief.*

