# Project context for SDK monorepo (agent-hub-sdks / marketplace-sdks)

## What is Agent Hub?

**Agent Hub** (separate repo: `agent-hub`) is a SaaS platform for marketing, monetizing, and managing **AI Agents and MCP Servers**. Developers list agents, set pricing (subscriptions, pay-per-use, credits, tiered), and earn revenue. The platform provides:

- **Secure API access** – agent keys, HMAC-signed requests, subscription/quota checks  
- **Authentication** – AWS Cognito for end users; agent keys for API callers  
- **Usage tracking and billing** – usage reporting, progress/completion for async, Stripe integration  
- **API gateway** – forwards requests to agent backends with signed headers and optional async callbacks  

The SDK monorepo you are building provides **client SDKs and integrations** so that:

1. **Callers** can invoke agents on Agent Hub (validate keys, call gateway invoke endpoints).  
2. **Backends** (agent implementers) can receive requests from the gateway, verify HMAC, extract user context, and report usage (and progress/completion for async).

---

## Agent Hub services relevant to SDKs

Only three services from Agent Hub are used by the SDKs. Base URLs are configurable. **When deployed to ECS**, each service uses a path prefix of the form **`/{service}/api/v1`** for consistency; local/Docker may use different path layouts.

| Service   | Role for SDKs | Typical port | Path prefix (local/default) | Path prefix (ECS) |
|-----------|----------------|-------------|-----------------------------|-------------------|
| **Gateway** | Invoke agents (sync/async); sends HMAC-signed requests to agent backends | 8083 | `/api` (controller-level) | `/gateway/api/v1` (controller-level) |
| **Core**    | Agent/key/subscription data; **agent key validation** | 8081 | `/api/v1` (servlet context) | `/core/api/v1` (servlet context) |
| **Usage**   | **Usage report**, **progress**, **completion** (async); quota | 8084 | `/api/usage` (controller under context `/`) | `/usage/api/v1` (servlet context; controller path ``) |

- **Gateway peculiarity:** The path prefix (`/api` or `/gateway/api/v1`) is set at **controller** level (`agent-hub.gateway.controller-path`), not as the servlet context-path (which stays `/`).  
- **Security service** (Cognito, user auth) is not used by these SDKs; callers use **agent keys** (Bearer token) for gateway invoke and for validate.  
- **Messaging** and **Gateway-usage-consumer** are internal; SDKs do not call them.

---

## API surface summary (source of truth: the plan)

Use configurable base URLs per service (`gatewayBaseUrl`, `coreServiceUrl`, `usageServiceUrl`). Paths below are appended to the appropriate base; when deployed to ECS, the base typically already includes the service and `/api/v1` (e.g. `https://api.example.com/core/api/v1` for core).

| Concern | Service | Method | URL (append to service base URL) |
|---------|---------|--------|-----------------------------------|
| **Invoke (sync)** | Gateway | POST/GET/PUT/DELETE | `{gatewayPath}/agent/{agentId}/endpoint/{endpointId}/invoke` — where `gatewayPath` is `/api` (default) or `/gateway/api/v1` (ECS). |
| **Invoke (async)** | Gateway | POST/GET | Same as above with `/invoke/async`; response has `requestId`, `progressUrl`, `callbackUrl`. Auth: `Authorization: Bearer <agentKey>`. |
| **Validate agent key** | Core | POST | `{corePath}/agent-keys/validate` — where `corePath` is `/api/v1` (default) or `/core/api/v1` (ECS). Body: `{ "agentKey", "agentId", "agentSecret" }`. Response: HMAC in `X-Marketplace-Signature`, `X-Marketplace-Timestamp`; body has `valid`, `userId`, `agentId`, `plan`, `roles`, `quotaRemaining`, `subscriptionActive`. **Verify response HMAC** as `HMAC(responseBody + timestamp, agentSecret)`. |
| **Report usage** | Usage | POST | `{usagePath}/report` — where `usagePath` is `/api/usage` (default) or `/usage/api/v1` (ECS). Body: `{ userId, agentId, unitsUsed, timestamp }`. Headers: `X-Marketplace-Signature`, `X-Marketplace-Timestamp` (signature = HMAC(body + timestamp, agentSecret)). |
| **Progress (async)** | Usage | POST | `{usagePath}/progress/{requestId}` — same `usagePath` as above; or use the full `progressUrl` from the async response. Body: `{ stage, percentageComplete, errorMessage?, timestamp }`. Sign: HMAC(body + timestamp, agentSecret). |
| **Completion (async)** | Usage | POST | `{usagePath}/complete/{requestId}` — same `usagePath`; or use the full `callbackUrl` from the async response. Body: `{ status, result?, resultUrl?, contentType?, units?, timestamp }`. Same signing. |

**Headers from gateway → agent backend:**  
`X-Marketplace-Signature`, `X-Marketplace-Timestamp`, `X-Marketplace-User-ID`, `X-Marketplace-Plan`, `X-Marketplace-Roles`, `X-Marketplace-Quota-Remaining`, `X-Marketplace-Subscription-Active`

---

## HMAC (cross-language)

- **Algorithm:** HMAC-SHA256; key and message in **UTF-8**; output **Base64**-encoded.  
- **Inbound (gateway → agent):** Canonical string = `payload + timestamp + userContextString` (no separators).  
  - `userContextString` = `userId ?? ""` + `plan ?? ""` + `roles.join(",")` + `quotaRemaining.toString()`.  
- **Outbound (report / progress / complete):** Canonical string = `bodyString + timestamp`.  
- **Validation response (core → client):** Response body (JSON string) + timestamp; HMAC with agentSecret; compare to `X-Marketplace-Signature`.  
- Use **constant-time comparison** for signatures. Optional: timestamp window (e.g. ±5 minutes) for replay protection.

The plan and `docs/hmac-spec.md` in the SDK repo should include test vectors; each language implements the same behavior.

---

## Java reference implementation (in this repo)

The **client** and the **lib** code it depends on (e.g. HMAC utilities) have been **moved/copied into this SDK monorepo** (e.g. under `sdk-java/` or `client/` and vendored `lib/`). The new repo does not have access to the Agent Hub repo unless both are opened together. Use the **Java code in this repo** as the reference for API surface, HMAC behavior, and HTTP contracts when implementing other languages or fixing the Java SDK.

**Reference files in this repo (paths may vary by layout):**

- **MarketplaceRequestVerifier** – `verifyHmacSignature`, `buildUserContextString`, `extractUserContext`, `UserContext`  
- **UsageServiceClient** – `sendProgressUpdate`, `sendCompletion`, `reportUsage`; progress/complete URL parsing and body signing  
- **AgentKeyValidationClient** – `validateAgentKey`, request/response DTOs, response HMAC check, optional cache  
- **HmacUtils** (vendored from lib) – `calculateHmac(data, key)`, `calculateHmacWithTimestamp(data, timestamp, key)` (canonical = `data + timestamp`)  
- **Models:** `UsageReportRequest`, `UsageReportResponse`

Other language SDKs should implement the same logical surface and HMAC spec; they do not call back into the Agent Hub repo.

---

## Naming and placeholder

- **Package/product name placeholder:** `marketplace` (replace with final product name before first public release).  
- Use one placeholder everywhere (npm: `@marketplace/agent-sdk`, NuGet: `Marketplace.AgentSdk`, PyPI: `marketplace-agent-sdk`, Maven: `com.marketplace:agent-sdk`, etc.).  
- Keep a short note in the SDK repo (e.g. `PACKAGE_NAME.md` or README): “Current package name placeholder: `marketplace`. Replace with final product name before first public release.”

---

## What this repo contains (SDK monorepo)

- **docs/** – Shared specs: `hmac-spec.md`, optional `api-overview.md` (and test vectors).  
- **sdk-java**, **sdk-js**, **sdk-dotnet**, **sdk-python**, **sdk-go**, **sdk-rust**, **sdk-ruby**, **sdk-php** – One directory per language; same logical API surface and HMAC behavior.  
- **integration-n8n** – n8n community nodes (trigger with HMAC verification, invoke, progress, complete, validate key).  
- **integration-openclaw** – OpenClaw plugin: Mode A = caller (invoke agents), Mode B = backend (verify HMAC, report usage).  

Each `sdk-*` and `integration-*` is self-contained (own build, deps, tests). No shared polyglot code; HMAC and API behavior are specified in docs and implemented per language.

---

## Implementation order (from the plan)

1. Add `docs/hmac-spec.md` and `docs/api-overview.md` (and test vectors).  
2. **sdk-java** – Client and vendored lib are already in this repo; remove any agent-hub dependency; publish under placeholder.  
3. **sdk-js** – Full surface + HMAC; then use in n8n and OpenClaw.  
4. **integration-n8n** – Nodes (use JS SDK or same HMAC spec).  
5. **integration-openclaw** – Mode A (tools + skill), then Mode B (handler or SDK-based).  
6. Remaining SDKs (C#, Python, Go, Rust, Ruby, PHP) in any order.

---

## Checklist reminder (for the building agent)

- New repo: root README + PACKAGE_NAME.md.  
- `docs/hmac-spec.md` (and optional `api-overview.md`) with test vector(s).  
- sdk-java: client and vendored lib are already in this repo; remove any leftover agent-hub dependency; publish with placeholder groupId/artifactId.  
- Each sdk-* and integration: unified API surface and HMAC spec; README with install + minimal example.  
- CI: per-folder test and publish.  
- Before release: replace `marketplace` with final product name.
