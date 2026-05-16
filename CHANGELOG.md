# Changelog

## 1.0.0 (development / pre-release)

SDKs are versioned **1.0.0** while the product is still in development.

### Gateway inbound HMAC

- Canonical string after `payload + timestamp` includes, in order: user id, plan, roles CSV, quota string, then **`subscriptionActive`** as `"true"` or `"false"`, then **`billingModelType`**, **`measurementType`**, and **`unitLabel`** (each `""` when absent). See [docs/hmac-spec.md](docs/hmac-spec.md) and [docs/sdk-api-spec.md](docs/sdk-api-spec.md) §4.

### Added

- **SDK ↔ MAIN-SDK-API-SPEC alignment:** Usage report request body uses ISO-8601 `timestamp`; `X-AgentVend-Timestamp` uses **Unix epoch seconds** for signing (Java, JS, Python, .NET). Full **usage report** response fields (`warning`, `remainingTimeUnitsPerPeriod`, `remainingSpendingCap`, `overageRate`) parsed in .NET and Python. **Gateway invoke** (sync/async) and Core **JWT usage estimate** (`POST …/billing/usage/estimate`) exposed on unified clients / modules. Python default Core path prefix is **`/api/v1`** (was ECS-style default).
- **Headers:** `X-AgentVend-Billing-Model`, `X-AgentVend-Measurement-Type`, `X-AgentVend-Unit-Label` (optional for HMAC).
- **Validate service key response:** Optional nullable `serviceKeyId` (UUID) on success JSON; surfaced on `ServiceKeyValidationResult` / equivalent types in Java, JavaScript, Python, and .NET. Java **0.0.3**, Python **0.0.4**, .NET **0.0.5**, JavaScript **0.0.5**.
- **Validate service key response:** Optional `billingModelType`, `measurementType`, `unitLabel` on success JSON (SDK result types updated in Java, JavaScript, Python, .NET, Rust).
- **Helpers:** e.g. `buildGatewayUserContextString` / `BuildGatewayUserContextString` / `build_gateway_user_context_string` where exposed.

### Notes

- Validate-response HMAC is unchanged: still `responseBody + timestamp`.
- Integrations (n8n, OpenClaw) use `verifySignatureFromHeaders` so signing headers are included automatically.
