# Changelog

## 1.0.0 (development / pre-release)

SDKs are versioned **1.0.0** while the product is still in development.

### Gateway inbound HMAC

- Canonical string after `payload + timestamp` includes, in order: user id, plan, roles CSV, quota string, then **`subscriptionActive`** as `"true"` or `"false"`, then **`billingModelType`**, **`measurementType`**, and **`unitLabel`** (each `""` when absent). See [docs/hmac-spec.md](docs/hmac-spec.md) and [docs/sdk-api-spec.md](docs/sdk-api-spec.md) §4.

### Added

- **Headers:** `X-AgentVend-Billing-Model`, `X-AgentVend-Measurement-Type`, `X-AgentVend-Unit-Label` (optional for HMAC).
- **Validate agent key response:** Optional nullable `agentKeyId` (UUID) on success JSON; surfaced on `AgentKeyValidationResult` / equivalent types in Java, JavaScript, Python, and .NET. Java **0.0.3**, Python **0.0.4**, .NET **0.0.5**, JavaScript **0.0.5**.
- **Validate agent key response:** Optional `billingModelType`, `measurementType`, `unitLabel` on success JSON (SDK result types updated in Java, JavaScript, Python, .NET, Rust).
- **Helpers:** e.g. `buildGatewayUserContextString` / `BuildGatewayUserContextString` / `build_gateway_user_context_string` where exposed.

### Notes

- Validate-response HMAC is unchanged: still `responseBody + timestamp`.
- Integrations (n8n, OpenClaw) use `verifySignatureFromHeaders` so signing headers are included automatically.
