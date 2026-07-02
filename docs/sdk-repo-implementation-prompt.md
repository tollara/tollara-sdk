# Tollara SDK — implementation prompt

> **Historical note:** This prompt describes the **HMAC v2 / estimate v1** rollout. The current contract is **validation v3**, **gateway HMAC v3**, **estimate v3**, and **report v2** — see [docs-sdk/MAIN-SDK-API-SPEC.md](../docs-sdk/MAIN-SDK-API-SPEC.md) and [docs-sdk/fixtures/](../docs-sdk/fixtures/). Sections below are retained for context only.

Use this document as the single copy-paste brief when updating the **tollara-sdk** repository. It was extracted from an internal platform plan (HMAC v2 / estimate docs).

---

**Task (superseded):** Implement **estimateUsage** (service-key pre-flight) across all SDK languages (Java, JS/TS, .NET, Python, etc.) for Tollara, plus **HMAC v2** and **validate schema v2** updates.

**Current contract (v3):** **`serviceProductId`**, **`subscriptionStatus`**, **`validationSchemaVersion: 3`**, **`grantAccess`**, gateway **`buildV3`** / signing version `"3"`, estimate **`estimateSchemaVersion: 3`** with balances on **`breakdown`** only, report **`reportSchemaVersion: 2`**.

### estimateUsage (agent backend, same trust as validate)

**URL:**

- `POST {tollaraBaseUrl}/core/api/v1/service-keys/estimate-usage`

**Auth:** **None** (no Bearer). Trust is **serviceKey** + **serviceSecret** in the JSON body, identical rules to `POST .../service-keys/validate` (optional `serviceId` when key alone resolves the service; if `serviceSecret` is sent non-empty it must match the service).

**Request JSON** (shape implemented in platform core as `EstimateUsageAgentKeyRequest`; SDK only needs the contract below):

- `serviceKey`: string (required).
- `serviceId`: string UUID (optional; omit to resolve from key like validate).
- `serviceSecret`: string (optional when only key is used; if present non-empty, must match server-side secret).
- `estimatedUnits`: number (required; must be **> 0**; decimal values allowed where the product’s unit model allows fractions).

**Response JSON (200)** — explicit fields (do **not** depend on Tollara Java types; this is the wire contract):

| Field | JSON type | Always present on 200? | Meaning |
| ----- | --------- | ---------------------- | ------- |
| `sufficientCredits` | boolean | yes | For **PREPAID**, whether remaining credits cover the estimated charge. For **SUBSCRIPTION** / **USAGE_POSTPAID**, typically `true` (credits model N/A). For **USAGE_INSTANT**, `true` when estimating is allowed (cap/cost logic still reflected in `wouldExceedCap` / `estimatedCost`). |
| `wouldExceedCap` | boolean | yes | Whether this estimate is rejected under the **same rules as usage record**: spending cap vs cumulative overage cost, and (subscription/postpaid) **surplus overage units** when a cap applies; **USAGE_INSTANT** when spend would exceed cap. |
| `wouldAllow` | boolean | yes | `true` iff core returns **HTTP 200** for this estimate (single flag for backends; aligned with `sufficientCredits` / `wouldExceedCap` gating). |
| `estimatedCost` | number or `null` | often set | Estimated **overage / charge** amount for this `estimatedUnits` (product- and model-specific; server uses the same rules as billing estimate). May be `null` when not applicable. |
| `remainingCredits` | number or `null` | optional | **PREPAID:** remaining credit balance (or analogous). **Other models:** usually `null`. |
| `remainingSpendingCap` | number or `null` | optional | Headroom under the spending cap after this estimate, when the model exposes it; otherwise `null`. |
| `billingModelType` | string | yes | e.g. `PREPAID`, `SUBSCRIPTION`, `USAGE_POSTPAID`, `USAGE_INSTANT`. |
| `measurementType` | string or `null` | often | e.g. `PER_REQUEST`, `PER_TIME_UNIT`, `PER_TOKEN`, `PER_BYTE`. |
| `unitLabel` | string or `null` | often | Config label (e.g. `request`). |
| `breakdown` | object or `null` | often | **SUBSCRIPTION** / **USAGE_POSTPAID:** calculator snapshot (`unitsUsed`, `baseUnitsUsed`, `overageUnits`, `chargeableOverageUnits`, `surplusOverageUnits`, costs, `unitsRemaining`, `remainingSpendingCap`, `totalUnitsUsedThisCycle`, `isOverLimit`, `isOverage`, `isOverageAllowed`). **PREPAID:** synthetic aligned fields. **USAGE_INSTANT:** usually `null`. Omitted-null subfields may be absent. |
| `estimateSchemaVersion` | integer | yes | Starts at **1**; bump if the JSON shape of this response changes (SDKs can branch on this). JSON may gain fields without a bump in pre-production; always verify HMAC on raw bytes. |
| `timestamp` | integer (64-bit safe) | yes | Unix epoch **seconds**; included in the signed body and aligns with `X-Tollara-Timestamp` for verification. |

**Semantics of HTTP status vs body:** On **403** / **429**, core may still return a **body** with the same field names and a **signed** response (check headers). Treat non-2xx as errors for application logic; still verify HMAC when the server sends signature headers.

**HMAC verification (required on success paths that return signatures):**

1. Read the raw response body as a **string** (UTF-8) **exactly** as received over the wire — do **not** re-`JSON.stringify` a parsed object for verification (field order, omitted nulls, and number formatting must match the server).
2. Read `X-Tollara-Timestamp` (string of decimal digits).
3. Compute `canonical = rawBody + timestamp` (string concatenation, **no** separator between body and timestamp).
4. `signature = Base64(HMAC-SHA256(canonical, agentSecretUTF8))` (same as `POST .../agent-keys/validate` response verification).
5. Constant-time compare to `X-Tollara-Signature`.

**Non-200:** treat as error; **401** responses may be **unsigned** (same as validate error behavior).

**HTTP status codes:** **200** success; **403** insufficient credits or forbidden; **429** would exceed cap; **400** billing rule / validation failures; **401** bad key, secret, or agent resolution.

### Authentication: why agentKey in the body, not Authorization: Bearer?

`POST .../agent-keys/validate` and `POST .../agent-keys/estimate-usage` are wired the same way in core:

- Spring Security: **permitAll()** on these paths — there is **no** OAuth2/JWT requirement and the **agent key is not** read from `Authorization`.
- **Trust model:** the client sends **agentKey** (and optionally **agentId**, **agentSecret**) in the **JSON body**. The controller resolves the agent, validates the key, and optionally checks `agentSecret` against the stored agent secret before returning a **signed** JSON response.

**Why not Bearer on core?** The **gateway** uses `Authorization: Bearer <agentKey>` for **invoke** (caller-facing API). Core **validate** / **estimate** are **server-to-server** endpoints designed as a single JSON document: one POST with key + secret + parameters, easy to document in OpenAPI, and symmetric with how agents already integrate validate. Using Bearer only for the key would split credentials across headers and body (`agentSecret` still in body), with no security gain for this trust model.

**Summary:** Auth on validate = **application-level** proof inside the request body (valid key + optional secret match), not HTTP Bearer on core.

### Gateway HMAC v2 + validate response

**User context string** for proxied invokes: literal **`"2"`** then concatenate: `userId`, `plan`, `roles` (comma-joined if any), `subscriptionActive` (`"true"`/`"false"`), `billingModelType`, `measurementType`, `unitLabel` (empty string for nulls per `GatewayHmacUserContext`). **No quota segment.** Optional header `X-Tollara-Signing-Version: 2`.

**Validate:** Response JSON includes `validationSchemaVersion: 2` and **no** `quotaRemaining`. Verify validate HMAC over **exact JSON body + timestamp** with `agentSecret` as today.

**Docs/tests:** README per language; unit tests with mocked HTTP for `estimateUsage`. **Note:** First-party apps that call core with a user Bearer token may use `POST /billing/usage/estimate` directly (not required for the agent SDK surface).

---

*Source: platform repo — derived from internal HMAC v2 / estimate documentation.*
