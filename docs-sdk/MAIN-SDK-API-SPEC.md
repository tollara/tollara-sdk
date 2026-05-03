# AgentVend SDK – API specification

## How this document is maintained

- **Canonical copy** lives in the AgentVend **agent-hub** repo at `docs-sdk/MAIN-SDK-API-SPEC.md`.
- The **AgentVend SDK** (separate **agentvend-sdk** repo) should receive an updated copy of this file whenever the HTTP contract changes so SDK implementations and release notes stay aligned.
- **Whenever you change** any SDK-facing path, method, header, status code, or JSON field described here (or add a new field to a documented response), you **must** update this document in the same change (or immediately after) and **append an entry to the changelog at the bottom of this file** (date, summary, sections touched). Do not skip the changelog.

This document specifies the exact HTTP APIs that the AgentVend SDK calls. Base URLs for each service are configurable; path prefixes depend on deployment (default vs ECS vs Docker).

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

- **HTTP status:** Mirrors the **agent backend** status when the gateway rejects the call before forwarding, or when the agent returns an error the gateway maps to a response (commonly **4xx/5xx** with a JSON **`ErrorResponse`**: `code`, `message`, optional `retryAfter` — e.g. `agent_error` with the agent error snippet in `message`).
- **Successful agent response (2xx) — default:** With **`gateway.event-driven-usage-recording.enabled`** **false** (default), the gateway returns **HTTP 200** and a JSON **`ResultResponse`** (not the raw agent bytes):
  - `requestId` (string)
  - `status`: `"COMPLETED"`
  - `result` (string): agent response body (may be large; treat as opaque payload)
  - `contentType` (string): agent `Content-Type` or `"text/plain"`
  - `resultUrl` (string, optional): usually null for gateway invoke
  - `size` (number): byte length hint; may be **0** if not populated
  - `completedAt` (string, ISO-8601 instant): gateway completion time
- **Successful agent response — event-driven path:** When **`gateway.event-driven-usage-recording.enabled`** is **true**, the usage event is published to Redis first; on **success** the gateway may return **HTTP 200** with the **raw agent body** (same shape as the agent returned) instead of `ResultResponse`. On publish failure it falls through to the default **`ResultResponse`** path above.
- **Sync streaming (`Accept: text/event-stream`):** Response is an **SSE stream**, not the JSON wrapper above.

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
| `callbackUrl` | string | **Usage** service URL to POST completion (`…/complete/{requestId}`). In the 202 body this path has **no** query string; completion requests must use **§3** header HMAC (agent secret). |
| `progressUrl` | string | **Gateway** URL for the **caller** to poll job status (`GET …/requests/{requestId}/status`). Not the same as snake_case `progress_url` in the agent-forwarded body (§1.2 note below). |

**Note for caller (202 body):** Fields are **camelCase** (`requestId`, `callbackUrl`, `progressUrl`) as returned by the gateway.

**Note for agent backend (forwarded JSON body):** Separate from the 202 response: the gateway may send **snake_case** `request_id`, `payload`, `progress_url`, and `callback_url`. The `progress_url` / `callback_url` values point at the **usage** service and may include `signature` and `timestamp` **query parameters** from the gateway’s **inbound** HMAC (see §4). **Usage service** progress and completion still require **`X-AgentVend-Signature` and `X-AgentVend-Timestamp` headers** per §3 (body + timestamp signed with the **agent secret**); agents must not assume the query string replaces §3 signing. If the agent does not receive URLs, it may build paths as `{usageBaseUrl}{usagePathPrefix}/progress/{requestId}` and `{usageBaseUrl}{usagePathPrefix}/complete/{requestId}` and sign requests per §3.

### 1.3 Optional: Job status (caller polling)

- **Method:** `GET`
- **URL:** `{gatewayBaseUrl}{gatewayPathPrefix}/requests/{requestId}/status`
- **Headers:** `Authorization: Bearer {agentKey}` (if required by deployment).

**Response**

- **HTTP 404** — Unknown `requestId`: JSON **`ErrorResponse`** (`code`: `request_not_found`, `message`, `retryAfter` optional). No `StatusResponse` body.
- **HTTP 200** — Row found: JSON **`StatusResponse`**:
  - `requestId`, `status` (string: `JobStatus` name such as **`PENDING`**, **`RUNNING`**, **`COMPLETED`**, **`FAILED`**; or **`ERROR`** if status lookup failed internally)
  - `startTime` (ISO-8601 instant or null)
  - `currentStage` (string or null): latest progress stage
  - `percentageComplete` (number, int 0–100)
  - `errorMessage` (string or null)
  - `elapsedHours`, `currentCost` (numbers; billing/elapsed hints)

### 1.4 Optional: Job result (caller polling)

- **Method:** `GET`
- **URL:** `{gatewayBaseUrl}{gatewayPathPrefix}/requests/{requestId}/result`
- **Headers:** `Authorization: Bearer {agentKey}` (if required).

**Response**

- **HTTP 404** — Unknown `requestId`: JSON **`ErrorResponse`** (`code`: `request_not_found`, …), same as §1.3.
- **HTTP 202** — Job not finished: JSON **`ResultResponse`** with `status` **`PENDING`** or **`RUNNING`**; `result` contains a short status message; `completedAt` may be absent.
- **HTTP 500** — Job finished with failure: JSON **`ResultResponse`** with `status` **`FAILED`**; `result` / `errorMessage`-style text may appear in `result`.
- **HTTP 200** — Terminal success: JSON **`ResultResponse`** with `status` **`COMPLETED`** (agent payload in `result`, plus `contentType`, `resultUrl` if stored, `completedAt`, `size` as available).
- **HTTP 200** with `status` **`ERROR`** — Rare: result retrieval error; `result` holds a diagnostic string (check `status` before treating as completed work).

### 1.5 `PER_TIME_UNIT` billing, streaming endpoints, and Core product validation

**Endpoint flag `isStreaming`:** Each API endpoint in Core may include `isStreaming` (JSON: `isStreaming`). When `true`, the route is allowed to be invoked as **sync** with **`Accept: text/event-stream`** so the gateway can bill wall-clock time for `PER_TIME_UNIT` subscriptions. **Async** endpoints use `isAsync` instead; `isStreaming` applies only to **sync** streaming. If `isAsync` is enabled, streaming sync is not used for that route.

**Gateway (subscribers, non–agent-owner):** When the active subscription resolves to **`PER_TIME_UNIT`** measurement:

- **Async invoke** (`…/invoke/async`) is allowed if the endpoint is configured as async (`isAsync`).
- **Sync JSON** invoke (no SSE negotiation) returns **400** with a stable error `code` **`time_unit_invocation_not_allowed`**.
- **Sync with SSE** (client negotiates `text/event-stream`, e.g. `Accept: text/event-stream`) is allowed only if the endpoint has **`isStreaming: true`**. If the client asks for SSE but the endpoint is not streaming, or if the endpoint is streaming but the client does not negotiate SSE and falls back to sync JSON, the gateway rejects with **400** and the same code where applicable.

**Agent owner** invocations skip this gate (owner path is not billed the same way).

**Units:** For a given `unitLabel` and wall-clock interval, streaming completion and non-streaming paths use the same time-unit conversion. When the label is **second**-based, elapsed time includes **fractional seconds** (nanosecond resolution, stored at six decimal places), not truncated whole seconds.

**Core – products:** Creating or updating an agent product whose effective measurement is **`PER_TIME_UNIT`** requires at least one **active** endpoint with **`isAsync` or `isStreaming`**. Otherwise Core returns **409 Conflict** with `code` **`per_time_unit_requires_async_or_streaming_endpoint`** and a short `message`. The gateway still enforces invoke-time rules above.

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
| `agentKeyId` | string (UUID) | **Present on success.** Database id of the validated agent key (`agent_keys.id`). Omitted or null on error responses. Included in the signed JSON; clients may persist it for correlation. |
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

- **Status:** typically **`401 Unauthorized`** with body `valid: false` and `error` message. **`500 Internal Server Error`** may occur with `valid: false` and `error` (e.g. internal failure). Response may still include HMAC headers; verify if present.

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

**Response body (JSON)** — same field set as the agent-key estimate body **excluding** `estimateSchemaVersion` and top-level `timestamp` (§2.3). **`@JsonInclude(NON_NULL)`** omits null-valued properties on this path.

**Integrity:** JWT responses are **not** HMAC-signed (no `X-AgentVend-Signature` / `X-AgentVend-Timestamp` from core). Only **§2.3** agent-key responses are signed.

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
| `timestamp` | string (ISO-8601) | no | Serialized as an **instant** string when using default JSON mapping; optional for signing (**header** timestamp is used for HMAC). |

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
| `timestamp` | string (ISO-8601) | no | Optional; header timestamp used for HMAC. |

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
| `status` | string | yes | **`JobStatus`** name (case-insensitive): **`COMPLETED`**, **`FAILED`**, or **`PENDING` / `RUNNING`** if ever sent. Unknown or blank values are treated as **`COMPLETED`** server-side; SDKs should send **`COMPLETED`** or **`FAILED`**. |
| `result` | string | no | Inline result (e.g. text). |
| `resultUrl` | string | no | URL to result (e.g. file). |
| `contentType` | string | no | Content type of result. |
| `units` | number | no | Units used (for billing); recommended when applicable. |
| `timestamp` | string (ISO-8601) | no | Optional; header timestamp used for HMAC. |

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
- **Validate response (core → SDK):** Verify `X-AgentVend-Signature` = Base64(HMAC-SHA256(responseBody + X-AgentVend-Timestamp, agentSecret)). Constant-time compare. Success bodies include `validationSchemaVersion: 2` (no `quotaRemaining`); success bodies may include **`agentKeyId`** (see §2.1).
- **JWT usage estimate (§2.2):** Response is **not** HMAC-signed; do not expect `X-AgentVend-*` signature headers.
- **Agent-key estimate response (core → SDK):** Same verification pattern as validate: `responseBody + timestamp` with `agentSecret`. Use the **raw** response body string; new fields (`wouldAllow`, `breakdown`, etc.) are included in the signed JSON automatically.
- **Gateway → agent (inbound):** `payload + timestamp + GatewayHmacUserContext` v2 (leading `"2"`, no quota).
- **Report / progress / completion (SDK → usage):** Send `X-AgentVend-Signature` = Base64(HMAC-SHA256(bodyJsonString + timestamp, agentSecret)) and `X-AgentVend-Timestamp` = timestamp (numeric string).

---

## Changelog

| Date | Summary |
|------|---------|
| 2026-05-03 | **`agentKeyId`** (§2.1, §6); async **202** vs agent URLs + **§3** signing (§1.2); **`GET …/result`** HTTP matrix (§1.4); maintenance + repo link renames to this path. **Spec review:** §1.1 default **`ResultResponse`** / event-driven raw body / SSE + **`ErrorResponse`** failures; §1.3 **404** + **`StatusResponse`**; §1.4 **404** + **`ResultResponse`** branches + **ERROR**; §2.1 **500**; §2.2 JWT **unsigned** + **`NON_NULL`**; §3 **ISO-8601** timestamps; §3.3 **`JobStatus`** strings; §6 JWT note. |

---

*Source: AgentVend platform (agent-hub repo). For use in AgentVend SDK; copy into agentvend-sdk when the contract changes. Path prefixes and base URLs are configurable per environment. Pair with `docs/sdk-repo-implementation-prompt.md` for a paste-ready SDK work brief.*

