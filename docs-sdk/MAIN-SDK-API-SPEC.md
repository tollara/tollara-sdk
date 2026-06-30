# Tollara SDK – API specification

## How this document is maintained

- **Canonical copy** lives in the Tollara **agent-hub** repo at `docs-sdk/MAIN-SDK-API-SPEC.md`.
- The **Tollara SDK** (separate **tollara-sdk** repo) should receive an updated copy of this file whenever the HTTP contract changes so SDK implementations and release notes stay aligned.
- **Whenever you change** any SDK-facing path, method, header, status code, or JSON field described here (or add a new field to a documented response), you **must** update this document in the same change (or immediately after) and **append an entry to the changelog at the bottom of this file** (date, summary, sections touched). Do not skip the changelog.

**Finding SDK-facing endpoints in platform source:** Platform controllers and services that implement the HTTP contract in this document are tagged in Javadoc with **`TOLLARA-SDK-ENDPOINT`** (literal string). Search the platform repo for that token (e.g. `rg TOLLARA-SDK-ENDPOINT`) to list classes and methods tied to the **tollara-sdk** surface. Tags usually cite the relevant section of this file (e.g. §2.1, §4).

This document specifies the exact HTTP APIs that the Tollara SDK calls. Base URLs for each service are configurable; path prefixes depend on deployment (default vs ECS vs Docker).

**Services and path prefixes:**

| Service | Default path prefix | ECS path prefix |
|--------|----------------------|------------------|
| Gateway | `/api` (controller path) | `/gateway/api/v1` |
| Core | `/api/v1` (servlet context) | `/core/api/v1` |
| Usage | `/api/usage` (controller under `/`) | `/usage/api/v1` (controller path empty) |

Full URL = `{baseUrl}{pathPrefix}{path}`. Example: Core validate = `{coreBaseUrl}/api/v1/service-keys/validate` (default) or `{coreBaseUrl}/core/api/v1/service-keys/validate` (ECS).

**Docker / no servlet context-path:** Some core deployments use `context-path: /`. In that case paths are still exposed as `/api/v1/service-keys/validate`, `/api/v1/service-keys/estimate-usage`, etc. (single segment after the host:port base URL). Do not duplicate `/api/v1` if your configured `coreBaseUrl` already ends with it.

---

## 1. Gateway – Invoke service (caller)

The SDK uses these when acting as a **caller** (invoking a service).

### 1.1 Sync invoke

**Request**

- **Method:** `GET` | `POST` | `PUT` | `DELETE`
- **URL:** `{gatewayBaseUrl}{gatewayPathPrefix}/service/{serviceId}/endpoint/{endpointId}/invoke`
  - `gatewayPathPrefix`: `/api` (default) or `/gateway/api/v1` (ECS)
- **Headers:**
  - `Authorization: Bearer {serviceKey}` (required)
  - `Content-Type: application/json` (for POST/PUT with body)
- **Body:** Optional. For POST/PUT, any JSON or raw body; forwarded to the service backend.
- **Query:** Optional query params forwarded to the backend.

**Response**

- **HTTP status:** Mirrors the **service backend** status when the gateway rejects the call before forwarding, or when the service returns an error the gateway maps to a response (commonly **4xx/5xx** with a JSON **`ErrorResponse`**: `code`, `message`, optional `retryAfter` — e.g. `service_error` with the service error snippet in `message`).
- **Successful service response (2xx) — default:** With **`gateway.event-driven-usage-recording.enabled`** **false** (default), the gateway returns **HTTP 200** and a JSON **`ResultResponse`** (not the raw service bytes):
  - `requestId` (string)
  - `status`: `"COMPLETED"`
  - `result` (string): service response body (may be large; treat as opaque payload)
  - `contentType` (string): service `Content-Type` or `"text/plain"`
  - `resultUrl` (string, optional): usually null for gateway invoke
  - `size` (number): byte length hint; may be **0** if not populated
  - `completedAt` (string, ISO-8601 instant): gateway completion time
- **Successful service response — event-driven path:** When **`gateway.event-driven-usage-recording.enabled`** is **true**, the usage event is published to Redis first; on **success** the gateway may return **HTTP 200** with the **raw service body** (same shape as the service returned) instead of `ResultResponse`. On publish failure it falls through to the default **`ResultResponse`** path above.
- **Sync streaming (`Accept: text/event-stream`):** Response is an **SSE stream**, not the JSON wrapper above.

### 1.2 Async invoke

**Request**

- **Method:** `GET` | `POST` | `PUT` | `DELETE`
- **URL:** `{gatewayBaseUrl}{gatewayPathPrefix}/service/{serviceId}/endpoint/{endpointId}/invoke/async`
- **Headers:** Same as sync (`Authorization: Bearer {serviceKey}`, optional `Content-Type`, etc.).
- **Body:** Optional; same as sync for POST/PUT.

**Response**

- **Status:** `202 Accepted` on success.
- **Body (JSON):**

| Field | Type | Description |
|-------|------|-------------|
| `status` | string | e.g. `"ACCEPTED"` |
| `requestId` | string | Unique ID for this async job. Use for progress/complete and status/result. |
| `callbackUrl` | string | **Usage** service URL to POST completion (`…/complete/{requestId}`). In the 202 body this path has **no** query string; completion requests must use **§3** header HMAC (service secret). |
| `progressUrl` | string | **Gateway** URL for the **caller** to poll job status (`GET …/requests/{requestId}/status`). Not the same as snake_case `progress_url` in the service-forwarded body (§1.2 note below). |

**Note for caller (202 body):** Fields are **camelCase** (`requestId`, `callbackUrl`, `progressUrl`) as returned by the gateway.

**Note for service backend (forwarded JSON body):** Separate from the 202 response: the gateway may send **snake_case** `request_id`, `payload`, `progress_url`, and `callback_url`. The `progress_url` / `callback_url` values point at the **usage** service and may include `signature` and `timestamp` **query parameters** from the gateway’s **inbound** HMAC (see §4). **Usage service** progress and completion still require **`X-Tollara-Signature` and `X-Tollara-Timestamp` headers** per §3 (body + timestamp signed with the **service secret**); services must not assume the query string replaces §3 signing. If the service does not receive URLs, it may build paths as `{usageBaseUrl}{usagePathPrefix}/progress/{requestId}` and `{usageBaseUrl}{usagePathPrefix}/complete/{requestId}` and sign requests per §3.

### 1.3 Optional: Job status (caller polling)

- **Method:** `GET`
- **URL:** `{gatewayBaseUrl}{gatewayPathPrefix}/requests/{requestId}/status`
- **Headers:** `Authorization: Bearer {serviceKey}` (if required by deployment).

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
- **Headers:** `Authorization: Bearer {serviceKey}` (if required).

**Response**

- **HTTP 404** — Unknown `requestId`: JSON **`ErrorResponse`** (`code`: `request_not_found`, …), same as §1.3.
- **HTTP 202** — Job not finished: JSON **`ResultResponse`** with `status` **`PENDING`** or **`RUNNING`**; `result` contains a short status message; `completedAt` may be absent.
- **HTTP 500** — Job finished with failure: JSON **`ResultResponse`** with `status` **`FAILED`**; `result` / `errorMessage`-style text may appear in `result`.
- **HTTP 200** — Terminal success: JSON **`ResultResponse`** with `status` **`COMPLETED`** (service payload in `result`, plus `contentType`, `resultUrl` if stored, `completedAt`, `size` as available).
- **HTTP 200** with `status` **`ERROR`** — Rare: result retrieval error; `result` holds a diagnostic string (check `status` before treating as completed work).

### 1.5 `PER_TIME_UNIT` billing, streaming endpoints, and Core product validation

**Endpoint flag `isStreaming`:** Each API endpoint in Core may include `isStreaming` (JSON: `isStreaming`). When `true`, the route is allowed to be invoked as **sync** with **`Accept: text/event-stream`** so the gateway can bill wall-clock time for `PER_TIME_UNIT` subscriptions. **Async** endpoints use `isAsync` instead; `isStreaming` applies only to **sync** streaming. If `isAsync` is enabled, streaming sync is not used for that route.

**Gateway (subscribers, non–developer):** When the active subscription resolves to **`PER_TIME_UNIT`** measurement:

- **Async invoke** (`…/invoke/async`) is allowed if the endpoint is configured as async (`isAsync`).
- **Sync JSON** invoke (no SSE negotiation) returns **400** with a stable error `code` **`time_unit_invocation_not_allowed`**.
- **Sync with SSE** (client negotiates `text/event-stream`, e.g. `Accept: text/event-stream`) is allowed only if the endpoint has **`isStreaming: true`**. If the client asks for SSE but the endpoint is not streaming, or if the endpoint is streaming but the client does not negotiate SSE and falls back to sync JSON, the gateway rejects with **400** and the same code where applicable.

**Developer** invocations skip this gate (owner path is not billed the same way).

**Units:** For a given `unitLabel` and wall-clock interval, streaming completion and non-streaming paths use the same time-unit conversion. When the label is **second**-based, elapsed time includes **fractional seconds** (nanosecond resolution, stored at six decimal places), not truncated whole seconds.

**Core – products:** Creating or updating a service product whose effective measurement is **`PER_TIME_UNIT`** requires at least one **active** endpoint with **`isAsync` or `isStreaming`**. Otherwise Core returns **409 Conflict** with `code` **`per_time_unit_requires_async_or_streaming_endpoint`** and a short `message`. The gateway still enforces invoke-time rules above.

---

## 2. Core – Validate service key

Used by both callers and backends to validate a service key and get user/entitlement snapshot. Response is HMAC-signed; the SDK **must** verify the signature. Numeric pre-flight uses **estimate** endpoints (§2.2–2.3), not validate.

### 2.1 Validate service key

**Request**

- **Method:** `POST`
- **URL:** `{coreBaseUrl}{corePathPrefix}/service-keys/validate`
  - `corePathPrefix`: `/api/v1` (default) or `/core/api/v1` (ECS)
- **Headers:** `Content-Type: application/json`
- **Body (JSON):**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `serviceKey` | string | yes | The service API key (Bearer value). |
| `serviceId` | string (UUID) | no | Service ID. If omitted, core may look it up from the key. |
| `serviceSecret` | string | no* | Service secret. *Required for HMAC verification of the response; client must have it to verify. |

**Response (success)**

- **Status:** `200 OK`
- **Headers:**
  - `X-Tollara-Signature`: HMAC-SHA256 (Base64) of `responseBody + timestamp` (timestamp as string, no separator), key = `serviceSecret`.
  - `X-Tollara-Timestamp`: Numeric string (Unix epoch seconds).
- **Body (JSON):**

| Field | Type | Description |
|-------|------|-------------|
| `valid` | boolean | `true` if key is valid. |
| `userId` | string | External user ID. |
| `serviceId` | string | Service UUID. |
| `serviceKeyId` | string (UUID) | **Present on success.** Database id of the validated service key (`service_keys.id`). Omitted or null on error responses. Included in the signed JSON; clients may persist it for correlation. |
| `serviceProductId` | string (UUID) | Subscribed product id; `"owner"` path may omit or use empty when not applicable. |
| `roles` | string[] | User roles (may be empty). |
| `validationSchemaVersion` | number | **`3`** on success. |
| `subscriptionStatus` | string | Uppercase status (e.g. `ACTIVE`, `TRIAL`, `CANCELLING`, `CANCELLING_PENDING`, `EXPIRED`). SDKs use `grantAccess(subscriptionStatus)` for invoke-eligible statuses. |
| `billingModelType` | string | Optional. `SUBSCRIPTION`, `USAGE_POSTPAID`, `USAGE_INSTANT`, `PREPAID`; omitted/null for owner path. |
| `measurementType` | string | Optional. e.g. `PER_REQUEST`, `PER_TIME_UNIT`, `PER_TOKEN`, `PER_BYTE`; omitted/null when not applicable. |
| `unitLabel` | string | Optional config label (e.g. `request`, `token`). |
| `timestamp` | number | Unix epoch seconds (same as header). |
| `error` | string | Present only on error (e.g. invalid key). |

**Common unsigned `error` strings (401, `valid: false`):**

| `error` | Meaning |
|---------|---------|
| `Invalid service key` | Unknown or invalid caller service key (caller fault). |
| `Invalid service secret` | `serviceSecret` in the validate request does not match the listing (seller fault). |

**Response (error)**

- **Status:** typically **`401 Unauthorized`** with body `valid: false` and `error` message. **`500 Internal Server Error`** may occur with `valid: false` and `error` (e.g. internal failure). Response may still include HMAC headers; verify if present.

**SDK must:**

1. Compute `canonical = responseBodyJsonString + X-Tollara-Timestamp` (string concatenation, no separator).
2. Compute `expectedSignature = Base64(HMAC-SHA256(canonical, serviceSecret))`.
3. Compare `expectedSignature` with `X-Tollara-Signature` using **constant-time** comparison.
4. If they differ, treat the response as invalid and do not trust the body.

### 2.1.1 Validate outcome (SDK)

All language SDKs expose **`validateServiceKeyWithOutcome`** (idiomatic naming per language) in addition to **`validateServiceKey`**, which remains a thin wrapper returning `null` on any failure.

**Success:** `{ ok: true, result: ServiceKeyValidationResult }`

**Failure:** `{ ok: false, code, message?, httpStatus? }`

**`code` values (exact strings, all SDKs):**

| Code | When |
|------|------|
| `MISSING_KEY` | Blank/whitespace service key (no HTTP call) |
| `NETWORK` | Transport/connection failure |
| `HTTP_ERROR` | Non-2xx response without a parseable unsigned error body (empty body, non-JSON, 5xx, etc.) |
| `MISSING_SIGNATURE_HEADERS` | 2xx body but missing `X-Tollara-Signature` or `X-Tollara-Timestamp` |
| `HMAC_MISMATCH` | Signature verification failed |
| `INVALID_KEY` | `valid: false` — from **unsigned** **401/403** JSON (set `message` from Core `error`), or from **2xx** body after HMAC verify |
| `PARSE_ERROR` | 2xx response body not valid JSON |

**Not a failure:** `valid: true` with a `subscriptionStatus` where `grantAccess(subscriptionStatus)` is `false` (e.g. `EXPIRED`) — returns success outcome; callers branch on `grantAccess` / subscription status.

### 2.2 Usage estimate (JWT)

**Request**

- **Method:** `POST`
- **URL:** `{coreBaseUrl}{corePathPrefix}/billing/usage/estimate`
- **Headers:** `Content-Type: application/json`, `Authorization: Bearer {userOrServiceJwt}`
- **Body (JSON):**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `userId` | string (UUID) | yes | Internal core user id (not Cognito sub). |
| `serviceId` | string (UUID) | yes | Service id. |
| `estimatedUnits` | number | yes | Positive; units to pre-check. |

**Authorization:** The principal must be allowed to estimate for `userId` (typically the end user for themselves, or a service/gateway principal with appropriate role).

**Response body (JSON)** — same field set as the service-key estimate body **excluding** `estimateSchemaVersion` and top-level `timestamp` (§2.3). **`@JsonInclude(NON_NULL)`** omits null-valued properties on this path.

**Integrity:** JWT responses are **not** HMAC-signed (no `X-Tollara-Signature` / `X-Tollara-Timestamp` from core). Only **§2.3** service-key responses are signed.

| Field | Type | Description |
|-------|------|-------------|
| `sufficientCredits` | boolean | **PREPAID:** enough credits for this `estimatedUnits`. Other models: `true` when credits are not used for the check. |
| `wouldExceedCap` | boolean | **Spending cap / surplus:** `true` when this estimate would be rejected for the same reasons as usage **record** (cumulative overage cost over cap, or surplus overage units on the request when a cap applies). **USAGE_INSTANT:** spend would exceed cap when configured. |
| `wouldAllow` | boolean | `true` iff core would return **200** for this estimate (aligned with `sufficientCredits` / `wouldExceedCap` gating). |
| `estimatedCost` | number or null | Estimated charge for this chunk where applicable. |
| `billingModelType` | string | e.g. `PREPAID`, `SUBSCRIPTION`, `USAGE_POSTPAID`, `USAGE_INSTANT`. |
| `measurementType` | string or null | e.g. `PER_REQUEST`, `PER_TIME_UNIT`, `PER_TOKEN`, `PER_BYTE`. |
| `unitLabel` | string or null | Config label (e.g. `request`). |
| `breakdown` | object or null | Calculator snapshot; **all balance/limit fields live here only** (see table below). |

**Rule:** No top-level field that also appears on `breakdown`. Do **not** expect top-level `remainingCredits` or `remainingSpendingCap` (estimate schema **3**).

**`breakdown` object** (when present): numeric fields such as `unitsUsed`, `baseUnitsUsed`, `overageUnits`, `chargeableOverageUnits`, `surplusOverageUnits`, `overageCost`, `totalOverageCost`, `unitsRemaining`, **`remainingCredits`** (PREPAID), `remainingSpendingCap`, `totalUnitsUsedThisCycle`, plus booleans `isOverLimit`, `isOverage`, `isOverageAllowed`. Omitted-null fields may be absent.

**HTTP status**

- **200 OK** — `wouldAllow` is `true`.
- **403 Forbidden** — Two cases: (1) **Authorization:** JWT principal is not allowed to estimate for the requested internal `userId` — core may return **403 with an empty body** (no JSON). (2) **Insufficient credits (PREPAID):** core returns **403 with a JSON body** (same estimate fields as above).
- **429 Too Many Requests** — Would exceed spending cap / surplus rules; body includes the estimate JSON above.
- **400 / 500** — Billing or server errors (see core logs); response may include a **minimal** estimate-shaped body (`wouldAllow` false) or an empty body depending on failure path.

### 2.3 Usage estimate (service key)

Same trust model as §2.1 (validate): **no Bearer**. Use when the backend only has **service key + service secret**.

**Request**

- **Method:** `POST`
- **URL:** `{coreBaseUrl}{corePathPrefix}/service-keys/estimate-usage`
- **Headers:** `Content-Type: application/json` only
- **Body (JSON):**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `serviceKey` | string | yes | Same as validate. |
| `serviceId` | string (UUID) | no | If omitted, core may resolve from key. |
| `serviceSecret` | string | no | If sent non-empty, must match server secret (same as validate). |
| `estimatedUnits` | number | yes | Positive. |

**Response (success body)**

Same business fields as §2.2 (`wouldAllow`, `breakdown`, etc.), plus:

| Field | Type | Description |
|-------|------|-------------|
| `estimateSchemaVersion` | number | **`3`**; bump if JSON shape changes. |
| `timestamp` | number | Unix epoch seconds (aligned with signing). |

**Headers on success:** `X-Tollara-Signature`, `X-Tollara-Timestamp` — verify **`HMAC-SHA256(responseBodyJson + timestamp, serviceSecret)`** (same concatenation rule as validate response).

**Status codes:** **200** allowed; **403** insufficient credits; **429** cap; **400** billing rule failure; **401** bad key/secret/service. **401** / some **400** / **500** responses may be **unsigned** and have an **empty** body (match validate error behavior). When status is **200**, **403** (credits), or **429**, the JSON body is signed if signature headers are present — verify HMAC on the **raw** body string + timestamp.

---

## 3. Usage service – Report, progress, completion

All three endpoints require request body + timestamp signed with **service secret** (not the service key). Algorithm: `canonical = bodyJsonString + timestamp` (timestamp = numeric string, same as header), then `signature = Base64(HMAC-SHA256(canonical, serviceSecret))`.

**Common headers for all three:**

- `Content-Type: application/json`
- `X-Tollara-Signature`: signature as above
- `X-Tollara-Timestamp`: numeric string (Unix epoch seconds)

**Server verification (progress and completion):** For **§3.2** and **§3.3**, the usage service validates HMAC using the **raw HTTP request body** (UTF-8 string exactly as received), not a re-serialized JSON form after parsing. SDKs and service backends must sign the **same byte sequence** they send in the request body. Semantically equivalent JSON with different field order, whitespace, or escaping will fail verification.

### 3.1 Report usage (non-proxied services)

**Request**

- **Method:** `POST`
- **URL:** `{usageBaseUrl}{usagePathPrefix}/report`
  - `usagePathPrefix`: `/api/usage` (default) or `/usage/api/v1` (ECS)
- **Headers:** As above (signature = HMAC(body + timestamp, serviceSecret)).
- **Body (JSON):**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `userId` | string | yes | External user ID. |
| `serviceId` | string (UUID) | yes | Service ID. |
| `unitsUsed` | number | yes | Units consumed (e.g. requests, tokens). |
| `timestamp` | string (ISO-8601) | no | Serialized as an **instant** string when using default JSON mapping; optional for signing (**header** timestamp is used for HMAC). |

**Response**

- **Status:** `200 OK`
- **Body (JSON):**

| Field | Type | Description |
|-------|------|-------------|
| `reportSchemaVersion` | number | **`2`** |
| `status` | string | e.g. `"ok"` |
| `warning` | string | Optional warning message. |
| `userId` | string | Echo of request `userId`. |
| `serviceId` | string | Echo of request `serviceId`. |
| `billingModelType` | string | e.g. `SUBSCRIPTION`, `PREPAID`. |
| `measurementType` | string | e.g. `PER_REQUEST`. |
| `unitLabel` | string | e.g. `request`. |
| `breakdown` | object or null | Shared usage breakdown (same shape as estimate §2.2). Read `breakdown.isOverLimit`, `breakdown.unitsRemaining`, `breakdown.remainingCredits` (PREPAID), etc. |

Legacy top-level fields (`isOverLimit`, `remainingRequestsPerPeriod`, `remainingTimeUnitsPerPeriod`, `remainingSpendingCap`, `overageRate`) are **removed** in schema **2**.

### 3.2 Progress (async jobs)

**Request**

- **Method:** `POST`
- **URL:** `{usageBaseUrl}{usagePathPrefix}/progress/{requestId}`  
  Or use the full `progress_url` from the async request body sent to the service (if present).
- **Headers:** Same as in §3 (signature = HMAC(body + timestamp, serviceSecret)).
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
  Or use the full `callback_url` from the async response or from the request body sent to the service.
- **Headers:** Same as in §3 (signature = HMAC(body + timestamp, serviceSecret)).
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

## 4. Headers sent from gateway to service backend (inbound)

The gateway signs a fixed **user-context suffix** after the request payload and timestamp (**schema v3**). Verification must use `GatewayHmacUserContext.buildV3` / the same field order as the gateway.

When the SDK is used in a **service backend** to verify incoming requests from the gateway, the gateway sends (among others):

| Header | Description |
|--------|-------------|
| `X-Tollara-Signature` | HMAC of `payload + timestamp + userContextString` (see below). |
| `X-Tollara-Timestamp` | Numeric string (Unix epoch seconds). |
| `X-Tollara-Signing-Version` | **`3`** when gateway uses HMAC user-context v3. |
| `X-Tollara-User-ID` | User ID. |
| `X-Tollara-Service-Product-ID` | Service product id (replaces legacy `X-Tollara-Plan`). |
| `X-Tollara-Roles` | Comma-separated roles (omit when empty). |
| `X-Tollara-Subscription-Status` | Uppercase subscription status (replaces legacy `X-Tollara-Subscription-Active`). |
| `X-Tollara-Billing-Model` | Optional. e.g. `SUBSCRIPTION`, `PREPAID`. |
| `X-Tollara-Measurement-Type` | Optional. e.g. `PER_REQUEST`, `PER_TOKEN`. |
| `X-Tollara-Unit-Label` | Optional. e.g. `request`, `token`. |

Do **not** expect `X-Tollara-Plan`, `X-Tollara-Subscription-Active`, or `serviceKeyId` as gateway headers.

**Verification (v3):** Let `payloadString` be the raw request body as a string (empty string if absent). Let `timestamp` be the long parsed from `X-Tollara-Timestamp`. Build `userContextString` = **`"3"`** + concatenation in this **exact** order (use `""` for null/absent strings):

1. `userId` (or `""`)
2. `serviceProductId` (or `""`)
3. If roles present: comma-joined roles; else nothing
4. `subscriptionStatus` (or `""`)
5. `billingModelType` or `""`
6. `measurementType` or `""`
7. `unitLabel` or `""`

Then `canonical = payloadString + timestamp + userContextString`. `signature = Base64(HMAC-SHA256(canonical, serviceSecret))`. Compare with constant-time equality to `X-Tollara-Signature`.

**SDK access:** Use `grantAccess(subscriptionStatus)` for invoke-eligible statuses: `ACTIVE`, `TRIAL`, `CANCELLING`, `CANCELLING_PENDING`.

**Legacy v2:** Leading `"2"` + `plan` + `Boolean.toString(subscriptionActive)` (no quota). **Legacy v1:** no version prefix; optional quota segment. SDKs should verify v3 in production; v1/v2 may remain for backward-compat tests only.

Reference implementation: `com.tollara.common.util.GatewayHmacUserContext` in **common-utils**.

For **async** invokes, the gateway signs the JSON serialization of the async body **before** `progress_url` / `callback_url` query params are added (same as before).

---

## 5. Summary table (SDK calls)

| SDK capability | Service | Method | Path (relative to path prefix) |
|----------------|---------|--------|----------------------------------|
| Invoke sync | Gateway | GET/POST/PUT/DELETE | `/service/{serviceId}/endpoint/{endpointId}/invoke` |
| Invoke async | Gateway | GET/POST/PUT/DELETE | `/service/{serviceId}/endpoint/{endpointId}/invoke/async` |
| Validate service key | Core | POST | `/service-keys/validate` |
| Usage estimate (JWT) | Core | POST | `/billing/usage/estimate` |
| Usage estimate (service key) | Core | POST | `/service-keys/estimate-usage` |
| Report usage | Usage | POST | `/report` |
| Progress | Usage | POST | `/progress/{requestId}` |
| Completion | Usage | POST | `/complete/{requestId}` |
| Job status (optional) | Gateway | GET | `/requests/{requestId}/status` |
| Job result (optional) | Gateway | GET | `/requests/{requestId}/result` |

---

## 6. HMAC summary (outbound from SDK)

- **Algorithm:** HMAC-SHA256; key = service secret (UTF-8); output = Base64.
- **Validate response (core → SDK):** Verify `X-Tollara-Signature` = Base64(HMAC-SHA256(responseBody + X-Tollara-Timestamp, serviceSecret)). Constant-time compare. Success bodies include `validationSchemaVersion: 3` with `serviceProductId` and `subscriptionStatus`; success bodies may include **`serviceKeyId`** (see §2.1). Use `grantAccess(subscriptionStatus)` for access checks.
- **JWT usage estimate (§2.2):** Response is **not** HMAC-signed; do not expect `X-Tollara-*` signature headers. Balances/caps only on `breakdown` (no top-level `remainingCredits` / `remainingSpendingCap`).
- **Service-key estimate response (core → SDK):** Same verification pattern as validate: `responseBody + timestamp` with `serviceSecret`. Success bodies use `estimateSchemaVersion: 3`.
- **Gateway → service (inbound):** `payload + timestamp + GatewayHmacUserContext` **v3** (leading `"3"`, `serviceProductId`, `subscriptionStatus`).
- **Report usage (SDK → usage, §3.1):** Response uses `reportSchemaVersion: 2` with identity + `breakdown` only.
- **Progress / completion (SDK → usage, §3.2–§3.3):** Same signing rule. The usage service verifies against the **raw request body** string (see §3 server verification note); sign exactly what you POST.

---

## 7. Marketplace service JSON (storefront / HTTP consumers)

Public marketplace and workspace list/detail responses that serialize **`Service`** include branding and optional marketing bullets as follows:

| Field | Meaning |
|------|---------|
| **`logoUrl`** | **Resolved** card logo URL for display: from the service’s uploaded **`cardLogoAssetId`** when set, otherwise the owner’s default branding asset, otherwise null. |
| **`coverImageUrl`** | **Resolved** wide cover image URL, same resolution order as `logoUrl`. |
| **`cardLogoAssetId`** | Optional FK to **`media_assets`** for a per-service logo override (may be null). |
| **`cardCoverAssetId`** | Optional FK to **`media_assets`** for a per-service cover override (may be null). |
| **`cardMarketing`** | Optional object `{ "features": [ { "id", "label", "displayOrder", "infoTooltip" } ] }` — ordered marketplace feature bullets (relational storage, not a JSON column on `services`). |

**Owner defaults:** **`Developer`** exposes **`brandingLogoAssetId` / `brandingCoverAssetId`** (uploaded media) plus read-only **`brandingLogoUrl` / `brandingCoverUrl`** resolved from those assets for display. Profile updates use **`marketplaceBranding`** `{ "logoAssetId", "coverAssetId" }` (null clears a slot). Sending this patch applies **both** slots; include the current id for a slot you do not intend to clear.

**Owner APIs (core-service):** `POST /developers/profile/marketplace-branding/media/upload` (multipart `file`) returns a **`MediaAsset`**; then `PUT /developers/profile` with `marketplaceBranding`.

**Developer APIs:** `PUT /api/v1/services/{id}/card-media` with `{ "cardLogoAssetId", "cardCoverAssetId" }` (null clears); `PUT /api/v1/services/{id}/card-marketing` with `{ "features": [ { "label", "infoTooltip" } ] }` (replaces all lines). Per-service images use the same **`media_assets`** pipeline as profile uploads (`POST …/services/{id}/profile/media/upload`).

---

## Changelog

| Date | Summary |
|------|---------|
| 2026-06-28 | **§2.1.1 validate outcome:** All SDKs expose `validateServiceKeyWithOutcome` with canonical failure codes (`MISSING_KEY`, `NETWORK`, `HTTP_ERROR`, etc.); `validateServiceKey` unchanged (returns null on failure). |
| 2026-06-28 | **Validation/gateway v3 + unified usage:** Validate `validationSchemaVersion: 3` with `serviceProductId`, `subscriptionStatus`; remove `plan`, `quotaRemaining`, `subscriptionActive`. Gateway HMAC v3 (`X-Tollara-Signing-Version: 3`, `X-Tollara-Service-Product-ID`, `X-Tollara-Subscription-Status`). Estimate `estimateSchemaVersion: 3` — balances/caps on `breakdown` only (`breakdown.remainingCredits` for PREPAID). Report `reportSchemaVersion: 2` with identity + `breakdown`. SDK `grantAccess(subscriptionStatus)`. §2.1–§2.3, §3.1, §4, §6. |
| 2026-06-23 | **§3 / §6 HMAC verification:** Documented that usage service **progress** and **completion** endpoints verify HMAC against the **raw HTTP request body**, not re-serialized JSON after parse. SDKs must sign the exact bytes they send. |
| 2026-05-05 | **Discovery + accuracy:** Documented **`TOLLARA-SDK-ENDPOINT`** Javadoc tag for finding platform implementations of this spec. **§2.1** `serviceKeyId` description now references DB table **`service_keys`** (replaces stale `agent_keys`). |
| 2026-05-04 | **Rename plan (agent-hub / platform):** SDK HTTP surface uses paths under **`/services`**, **`/developers`**, **`/service-keys`**, **`/service/{serviceId}/endpoint/{endpointId}/invoke`** (and related gateway/usage paths in §1–§5). Request/response JSON uses **`serviceKey`**, **`serviceId`**, **`serviceSecret`**, **`serviceKeyId`**, **`serviceProductId`**, and developer-oriented IDs/metadata aligned with DB migrations (e.g. **`developers`**, **`service_keys`**, **`service_products`**). Validate success bodies use **`validationSchemaVersion: 2`** (no `quotaRemaining`). Brand **`Tollara`**, **`X-Tollara-*`** headers, **`TOLLARA_*`** env vars, and SDK type names such as **`TollaraClient`** / **`TollaraRequestVerifier`** are unchanged. Marketplace publisher Cognito role is **`DEVELOPER`**; end-user role **`USER`**. Coordinate standalone **tollara-sdk** releases with this file when contract fields or paths change. |
| 2026-05-03 | **§7** Marketplace card: **`cardMarketing`**, **`cardLogoAssetId` / `cardCoverAssetId`**, resolved **`logoUrl` / `coverImageUrl`** from **`media_assets`**; owner **`marketplaceBranding`** + **`brandingLogoAssetId` / `brandingCoverAssetId`**; **`PUT …/card-media`** and **`PUT …/card-marketing`**. |
| 2026-05-03 | **`serviceKeyId`** (§2.1, §6); async **202** vs service URLs + **§3** signing (§1.2); **`GET …/result`** HTTP matrix (§1.4); maintenance + repo link renames to this path. **Spec review:** §1.1 default **`ResultResponse`** / event-driven raw body / SSE + **`ErrorResponse`** failures; §1.3 **404** + **`StatusResponse`**; §1.4 **404** + **`ResultResponse`** branches + **ERROR**; §2.1 **500**; §2.2 JWT **unsigned** + **`NON_NULL`**; §3 **ISO-8601** timestamps; §3.3 **`JobStatus`** strings; §6 JWT note. |

---

*Source: Tollara platform (agent-hub repo). For use in Tollara SDK; copy into tollara-sdk when the contract changes. Path prefixes and base URLs are configurable per environment. Pair with `docs/sdk-repo-implementation-prompt.md` for a paste-ready SDK work brief.*

