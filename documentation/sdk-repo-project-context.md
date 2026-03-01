# Project context for SDK monorepo (agent-hub-sdks / marketplace-sdks)

**Use this file in the new SDK repository.** Paste this into Cursor (e.g. as a rule, or in the first message) together with the plan file `.cursor/plans/sdk_monorepo_and_multi-language_sdks_d41e55d9.plan.md` so the agent has full context without the rest of the Agent Hub codebase.

---

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

Only three services from Agent Hub are used by the SDKs. Base URLs are configurable (e.g. `https://api.example.com`); typical local ports are listed for reference.

| Service        | Role for SDKs | Typical port | Context path   |
|----------------|----------------|-------------|----------------|
| **Gateway**    | Invoke agents (sync/async); sends HMAC-signed requests to agent backends | 8083 | `/api` |
| **Core**       | Agent/key/subscription data; **agent key validation** endpoint | 8081 | `/api/v1` |
| **Usage**      | **Usage report**, **progress**, **completion** (async); quota | 8084 | (root) |

- **Security service** (Cognito, user auth) is not used by these SDKs; callers use **agent keys** (Bearer token) for gateway invoke and for validate.  
- **Messaging** and **Gateway-usage-consumer** are internal to the platform; SDKs do not call them.

---

## API surface summary (source of truth: the plan)

- **Invoke (caller)**  
  - Sync: `POST/GET/PUT/DELETE` → `{gatewayUrl}/api/agent/{agentId}/endpoint/{endpointId}/invoke`  
  - Async: same path with `/invoke/async`; response has `requestId`, `progressUrl`, `callbackUrl`  
  - Auth: `Authorization: Bearer <agentKey>`

- **Validate agent key (caller or backend)**  
  - `POST {coreServiceUrl}/api/v1/agent-keys/validate`  
  - Body: `{ "agentKey", "agentId", "agentSecret" }`  
  - Response: HMAC in `X-Marketplace-Signature`, `X-Marketplace-Timestamp`; body has `valid`, `userId`, `agentId`, `plan`, `roles`, `quotaRemaining`, `subscriptionActive`.  
  - **Verify response HMAC** as `HMAC(responseBody + timestamp, agentSecret)`.

- **Report usage (backend, non-proxied)**  
  - `POST {usageServiceUrl}/api/usage/report`  
  - Body: `{ userId, agentId, unitsUsed, timestamp }`  
  - Headers: `X-Marketplace-Signature`, `X-Marketplace-Timestamp` (signature = HMAC(body + timestamp, agentSecret)).

- **Progress (async backend)**  
  - POST to `progressUrl` from async response.  
  - Body: `{ stage, percentageComplete, errorMessage?, timestamp }`  
  - Sign: HMAC(body + timestamp, agentSecret); timestamp can come from callback URL query.

- **Completion (async backend)**  
  - POST to `callbackUrl` (usage-service `/api/usage/complete/{requestId}`).  
  - Body: `{ status, result?, resultUrl?, contentType?, units?, timestamp }`  
  - Same signing: HMAC(body + timestamp, agentSecret).

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

## Java reference implementation (in Agent Hub repo)

When you need to align behavior or APIs, use the **Agent Hub** repo as reference (do not copy code into the SDK repo; implement per language from the spec and plan).

**Paths in the `agent-hub` repo:**

- `client/java/src/main/java/com/bugisiw/marketplace/client/MarketplaceRequestVerifier.java` – `verifyHmacSignature`, `buildUserContextString`, `extractUserContext`, `UserContext`  
- `client/java/.../UsageServiceClient.java` – `sendProgressUpdate`, `sendCompletion`, `reportUsage`; progress/complete URL parsing and body signing  
- `client/java/.../AgentKeyValidationClient.java` – `validateAgentKey`, request/response DTOs, response HMAC check, optional cache  
- `lib/common-utils/.../util/HmacUtils.java` – `calculateHmac(data, key)`, `calculateHmacWithTimestamp(data, timestamp, key)` (canonical = `data + timestamp`)  
- Models: `client/java/.../model/UsageReportRequest.java`, `UsageReportResponse.java`

The plan says: use the Java implementation as the **reference** for API surface, HMAC behavior, and HTTP contracts.

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
2. **sdk-java** – Migrate/copy from agent-hub `client/java`; remove/replace dependency on `lib/common-utils`; publish under placeholder.  
3. **sdk-js** – Full surface + HMAC; then use in n8n and OpenClaw.  
4. **integration-n8n** – Nodes (use JS SDK or same HMAC spec).  
5. **integration-openclaw** – Mode A (tools + skill), then Mode B (handler or SDK-based).  
6. Remaining SDKs (C#, Python, Go, Rust, Ruby, PHP) in any order.

---

## Checklist reminder (for the building agent)

- New repo: root README + PACKAGE_NAME.md.  
- `docs/hmac-spec.md` (and optional `api-overview.md`) with test vector(s).  
- sdk-java from agent-hub client/java; inline/vendor HMAC; placeholder groupId/artifactId.  
- Each sdk-* and integration: unified API surface and HMAC spec; README with install + minimal example.  
- CI: per-folder test and publish.  
- Before release: replace `marketplace` with final product name.
