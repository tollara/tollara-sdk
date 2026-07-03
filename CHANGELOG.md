# Changelog

## `n8n-nodes-tollara` 0.0.3

- Bundle `example-workflows/*.json` and `example-workflows/README.md` in the npm tarball.
- README: backend vs subscriber node grouping; import paths under `node_modules/n8n-nodes-tollara/example-workflows/`; simplified production API guidance.
- Maintainer docs in `LOCAL-DEVELOPMENT.md`.
- Depends on `@tollara/service-sdk` `^0.0.2` from npm (no `file:../sdk-js`).

---

## npm `@tollara/service-sdk` / `n8n-nodes-tollara` 0.0.2

Independent npm semver (not tied to HMAC `validationSchemaVersion` or `estimateSchemaVersion`).

### `@tollara/service-sdk` 0.0.2

- `validateServiceKeyWithOutcome` with canonical failure codes (§2.1.1).
- Hosted API auto-prefix for `api.tollara.ai`, PPE, and branded `*.api.tollara.ai` (ECS `/gateway/api/v1`, `/core/api/v1`, `/usage/api/v1`).
- `INVALID_KEY` mapping for unsigned 401/403 validate responses.

### `n8n-nodes-tollara` 0.0.2

- Auth nodes (Allowed / Denied outputs), structured `tollaraOk` on invoke/estimate/job nodes.
- Example workflows with explicit error paths; local fixture templating (`apply:local-fixture`).

---

## Platform contract 3.0.0 — Validation HMAC v3 + unified usage responses (breaking)

Coordinated breaking release with platform (core, gateway, usage).

### Breaking changes

- **Validate v3:** Remove `plan`, `quotaRemaining`, `subscriptionActive`. Add `serviceProductId`, `subscriptionStatus`, `validationSchemaVersion: 3`. Use `grantAccess(subscriptionStatus)` for `ACTIVE`, `TRIAL`, `CANCELLING`, `CANCELLING_PENDING`.
- **Gateway HMAC v3:** `buildV3` / `buildGatewayUserContextStringV3`; headers `X-Tollara-Service-Product-ID`, `X-Tollara-Subscription-Status`; signing version `3`. Remove reliance on `X-Tollara-Plan`, `X-Tollara-Subscription-Active`.
- **Estimate v3:** `estimateSchemaVersion: 3`; remove top-level `remainingCredits` and `remainingSpendingCap`; read from `breakdown` (including `breakdown.remainingCredits` for PREPAID).
- **Report v2:** `reportSchemaVersion: 2` with `userId`, `serviceId`, billing identity, and `breakdown` only.

See [docs-sdk/MAIN-SDK-API-SPEC.md](docs-sdk/MAIN-SDK-API-SPEC.md) §2–§4, §6.

---

## 2.0.0 — Tollara rebrand (breaking)

**AgentVend.ai is now Tollara.ai.** This is a coordinated breaking release with the platform (gateway, core, usage).

### Breaking changes

- **Product and packages:** All `agentvend` / `AgentVend` package IDs are replaced by `tollara` / `Tollara` (e.g. `com.tollara:service-sdk`, `@tollara/service-sdk`, `Tollara.ServiceSdk`, `tollara-service-sdk`). Previous AgentVend packages are deprecated.
- **HTTP headers:** `X-AgentVend-*` → **`X-Tollara-*`** (all 11 signed headers, including signing-version and billing headers).
- **Environment variables:** `AGENTVEND_*` → **`TOLLARA_API_URL`**, **`TOLLARA_SERVICE_ID`**, **`TOLLARA_SERVICE_SECRET`** (legacy `AGENTVEND_AGENT_*` aliases removed).
- **Default API origin:** **`https://api.tollara.ai`** (was `https://api.agentvend.api`).
- **Public API types:** `AgentVendClient` → **`TollaraClient`**, `AgentVendRequestVerifier` → **`TollaraRequestVerifier`**, etc., in every language SDK.
- **Integrations:** `n8n-nodes-tollara`, `openclaw-tollara`; n8n node/credential IDs renamed (existing workflows must be reconfigured).

### Migration

1. Update dependencies to Tollara package coordinates (see root [README.md](README.md)).
2. Rename env vars and header reads in your service backend to `TOLLARA_*` and `X-Tollara-*`.
3. Deploy platform and SDKs together; mixed AgentVend/Tollara headers are not supported.

---

## 1.0.0 (development / pre-release)

SDKs are versioned **1.0.0** while the product was still branded AgentVend.

### Gateway inbound HMAC

- Canonical string after `payload + timestamp` includes, in order: user id, plan, roles CSV, quota string, then **`subscriptionActive`** as `"true"` or `"false"`, then **`billingModelType`**, **`measurementType`**, and **`unitLabel`** (each `""` when absent).

### Added

- **SDK ↔ MAIN-SDK-API-SPEC alignment:** Usage report request body uses ISO-8601 `timestamp`; `X-Tollara-Timestamp` uses **Unix epoch seconds** for signing (Java, JS, Python, .NET). Full **usage report** response fields (`warning`, `remainingTimeUnitsPerPeriod`, `remainingSpendingCap`, `overageRate`) parsed in .NET and Python. **Gateway invoke** (sync/async) and Core **JWT usage estimate** (`POST …/billing/usage/estimate`) exposed on unified clients / modules. Python default Core path prefix is **`/api/v1`** (was ECS-style default).
- **Headers:** `X-Tollara-Billing-Model`, `X-Tollara-Measurement-Type`, `X-Tollara-Unit-Label` (optional for HMAC).
- **Validate service key response:** Optional nullable `serviceKeyId` (UUID) on success JSON; surfaced on `ServiceKeyValidationResult` / equivalent types in Java, JavaScript, Python, and .NET. Java **0.0.3**, Python **0.0.4**, .NET **0.0.5**, JavaScript **0.0.5**.
- **Validate service key response:** Optional `billingModelType`, `measurementType`, `unitLabel` on success JSON (SDK result types updated in Java, JavaScript, Python, .NET, Rust).
- **Helpers:** e.g. `buildGatewayUserContextString` / `BuildGatewayUserContextString` / `build_gateway_user_context_string` where exposed.

### Notes

- Validate-response HMAC is unchanged: still `responseBody + timestamp`.
- Integrations (n8n, OpenClaw) use `verifySignatureFromHeaders` so signing headers are included automatically.
