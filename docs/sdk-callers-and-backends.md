# Tollara SDK: Callers vs Agent Backends

This document explains **how the Tollara SDK is used** in the two main integration roles: **callers** (clients that invoke agents) and **agent backends** (your HTTP server, either **proxied** via the gateway or **non-proxied** and reached directly). It complements the normative HTTP details in **[sdk-api-spec.md](./sdk-api-spec.md)** and the SDK monorepo orientation in **[sdk-repo-project-context.md](./sdk-repo-project-context.md)**.

**Related docs**

| Document | Purpose |
|----------|---------|
| [sdk-api-spec.md](./sdk-api-spec.md) | Exact URLs, headers, bodies, HMAC rules for SDK implementers |
| [sdk-repo-project-context.md](./sdk-repo-project-context.md) | What lives in the separate SDK repo and how it relates to Agent Hub |

There is **no other doc** in this folder dedicated solely to caller vs backend *usage*; those files focus on API contracts and repository layout.

**Base URLs:** Unified `TollaraClient` APIs in Java, Python, JavaScript/TypeScript, Rust (`http` feature), and .NET default the API origin to **`https://api.tollara.ai`**. Set `TOLLARA_API_URL` (or the constructor/builder `apiUrl` / equivalent) only to override—for example staging or local stacks. Path prefixes default per [sdk-api-spec.md](./sdk-api-spec.md); override only for non-standard deployments. Low-level helpers still take explicit bases. Async flows should use the full `progressUrl` / `callbackUrl` values returned by the platform. See [api-overview.md](./api-overview.md) and [sdk-api-spec.md](./sdk-api-spec.md).

**Unified HTTP entry point:** Use `TollaraClient` (or `TollaraClient::try_new` / `TollaraClient.Create`) so you do not have to wire each service by hand; optional env `TOLLARA_API_URL` and optional split bases / path prefixes remain available when you need them.

---

## 1. Two roles in one platform

```mermaid
flowchart LR
  subgraph Caller["Caller application"]
    C[Your app / automation / integration]
  end

  subgraph Platform["Agent Hub platform"]
    GW[Gateway]
    CORE[Core]
    USAGE[Usage]
  end

  subgraph Backend["Agent backend"]
    AG[Your agent HTTP server]
  end

  C -->|"Bearer service key, invoke"| GW
  GW -->|"HMAC + X-Tollara-* headers"| AG
  C -.->|"optional: validate key"| CORE
  AG -->|"signed report / progress / complete"| USAGE
```

- **Caller** — Sits **outside** the agent’s server. For **proxied** agents it uses a **service API key** against the **gateway** invoke URL (and may optionally call **core** to validate a key). For **non-proxied** agents it typically sends **`Authorization: Bearer <serviceKey>`** directly to the agent’s public URL. Callers never see gateway HMAC headers.
- **Agent backend** — The HTTP server that implements the agent. **Proxied** backends receive **gateway-signed** requests (`X-Tollara-Signature`, user context headers). **Non-proxied** backends receive **Bearer** keys and must **validate** them with **core**. Both modes typically call the **usage** service to report consumption and (for async jobs) progress and completion.

### Proxied vs non-proxied (backends)

| | **Proxied** | **Non-proxied** |
|---|-------------|-----------------|
| **Caller’s entry URL** | Gateway invoke URL | Agent owner’s own HTTP base URL |
| **Who validates the service key first** | Gateway | Your backend (via core `validate`) |
| **Inbound cryptographic check on your server** | Verify **gateway HMAC** with **service secret** | **None** from gateway; use **`validateServiceKey`** + response HMAC to core |
| **Usage / progress / completion** | Signed POSTs to **usage** (service secret) | Same |

The published SDK exposes one logical surface; **which calls you use depends on role (caller vs backend) and proxied vs non-proxied**.

---

## 2. Callers: what they do and why use the SDK

A caller’s job is to **invoke** an agent (sync or async): for **proxied** agents that means the **gateway** invoke URL; for **non-proxied** agents it means the **agent’s own** URL. Callers may optionally **validate** a service key via **core** first. For **proxied** async flows they may **poll** job status or result on the gateway.

### 2.1 Sync invoke (proxied path via gateway)

For synchronous **proxied** invoke, the caller sends **`Authorization: Bearer <serviceKey>`** to the gateway. No HMAC is required on the caller’s side for this request.

```mermaid
sequenceDiagram
  participant Caller as Caller app
  participant GW as Gateway
  participant Agent as Agent backend

  Caller->>GW: HTTP invoke (Bearer service key, body/query)
  GW->>GW: Validate key, quota, routing
  GW->>Agent: Forward request + HMAC + X-Tollara-* headers
  Agent-->>GW: Response
  GW-->>Caller: Same status/body as agent (opaque)
```

**SDK value for callers (sync)**

- Mainly to check **entitlement** by calling validate key and to verify the response HMAC using the SDK.
- Correct URL shape and path prefixes (local vs ECS-style bases) — see [sdk-api-spec.md](./sdk-api-spec.md) §1.
- Typed helpers and shared conventions (e.g. parsing async body into object - requestId, progressUrlm callbackUrl); invoke alone can also be done with any HTTP client.

### 2.2 Optional: validate service key and get quota info

Callers that need **entitlement or validity** before invoking (or without going through the gateway) call **core** `POST .../service-keys/validate`. The response is **HMAC-signed**; clients that care about tampering **must** verify the signature using the **service secret** (same rules as in [docs-sdk/MAIN-SDK-API-SPEC.md](../docs-sdk/MAIN-SDK-API-SPEC.md) §2). Success bodies use **`validationSchemaVersion: 3`** with **`serviceProductId`** and **`subscriptionStatus`**; use **`grantAccess(subscriptionStatus)`** for invoke eligibility.

**SDK value:** Implements **response HMAC verification** (canonical string, constant-time compare), which is easy to get wrong when hand-rolled.

```mermaid
sequenceDiagram
  participant Caller as Caller app
  participant CORE as Core service

  Caller->>CORE: POST /service-keys/validate (serviceKey, serviceId, serviceSecret in body as required)
  CORE-->>Caller: JSON body + X-Tollara-Signature + X-Tollara-Timestamp
  Note over Caller: SDK verifies HMAC before trusting body
```

### 2.3 Async invoke and polling (proxied / gateway)

For **proxied** agents, async invoke on the gateway returns **202** with `requestId`, and URLs for **progress** (usage) and **callback** (completion), plus gateway URLs the **caller** may use to poll **status** and **result** ([sdk-api-spec.md](./sdk-api-spec.md) §1.2–1.4). **Non-proxied** async patterns depend on how the agent owner exposes their API; usage reporting from the backend still uses the same signed **usage** endpoints when applicable.

```mermaid
sequenceDiagram
  participant Caller as Caller app
  participant GW as Gateway
  participant Agent as Agent backend
  participant USAGE as Usage service

  Caller->>GW: .../invoke/async (Bearer service key)
  GW-->>Caller: 202 + requestId + progressUrl + callbackUrl (+ optional poll URLs)

  GW->>Agent: Forward async job (body may include request_id, progress_url, callback_url)
  loop While job runs
    Agent->>USAGE: POST progress (signed with service secret)
  end
  Agent->>USAGE: POST completion to callback URL (signed)

  Caller->>GW: Optional GET .../requests/{requestId}/status|result
  GW-->>Caller: Job state / result
```

**SDK value:** Encapsulates async response handling and optional polling paths aligned with the spec.

### 2.4 Caller-facing SDK capabilities (summary)

| Capability | Service | Caller needs SDK? |
|------------|---------|-------------------|
| Invoke sync/async | Gateway | **SDK not required** — any HTTP client + `Bearer` to the **gateway invoke URL** is standard; SDK only for convenience (prefixes, async helpers) |
| Validate key + verify response HMAC | Core | **Strongly recommended** if you call validate yourself (same whether you later invoke via gateway or not) |
| Poll job status/result | Gateway | Optional SDK convenience |

---

## 3. Agent backends: what they do and why use the SDK

Backends are either **proxied** (behind the gateway) or **non-proxied** (clients hit your URL directly). The SDK calls you rely on for **inbound trust** differ; **outbound** usage reporting is the same in both cases ([sdk-api-spec.md](./sdk-api-spec.md) §3).

### 3.1 Inbound: proxied agents (gateway-signed requests)

The gateway validates the caller’s service key, then forwards to your URL with **HMAC** and **`X-Tollara-*`** headers. You **must** verify that signature with the **service secret** before trusting the body or headers ([sdk-api-spec.md](./sdk-api-spec.md) §4).

```mermaid
sequenceDiagram
  participant GW as Gateway
  participant Agent as Agent backend

  GW->>Agent: Request + X-Tollara-Signature + X-Tollara-Timestamp + user context headers
  Note over Agent: SDK: verifySignature / getUserContext
  Agent->>Agent: Reject if invalid signature or stale timestamp
  Agent-->>GW: Business response
```

**SDK value:** **`verifySignature`** / **`getUserContext`** implement the canonical string and header rules. You typically **do not** need **`validateServiceKey`** on every request here because the gateway already validated the key.

### 3.2 Inbound: non-proxied agents (direct Bearer key)

Callers send **`Authorization: Bearer <serviceKey>`** to **your** server. There is **no** gateway HMAC. To obtain **user id, service product id, subscription status, roles**, and to reject bad keys, the backend calls **core** **`POST .../service-keys/validate`** and **must verify** the response HMAC with the **service secret** ([docs-sdk/MAIN-SDK-API-SPEC.md](../docs-sdk/MAIN-SDK-API-SPEC.md) §2). Success bodies use **`validationSchemaVersion: 3`**; use **`grantAccess(subscriptionStatus)`** for invoke eligibility. A reference pattern lives under `agents/non-proxied-agent` in this repo.

```mermaid
sequenceDiagram
  participant Caller as Caller app
  participant Agent as Agent backend
  participant CORE as Core service

  Caller->>Agent: Request + Bearer service key
  Agent->>CORE: validateServiceKey (serviceKey, serviceId, serviceSecret)
  CORE-->>Agent: JSON + X-Tollara-Signature + X-Tollara-Timestamp
  Note over Agent: SDK verifies response HMAC, then trusts body
  Agent-->>Caller: Business response
```

**SDK value:** **`validateServiceKey`** (with response HMAC verification) is the **primary** inbound trust mechanism for non-proxied agents. **`verifySignature`** for gateway HMAC does **not** apply.

### 3.3 Outbound: usage, progress, and completion (both modes)

After the request is authenticated (**proxied:** verified gateway HMAC; **non-proxied:** validated key via core), backends report **usage** and, for async work, **progress** and **completion** to the **usage** service. All use **HMAC** over `body + timestamp` with the **service secret** ([sdk-api-spec.md](./sdk-api-spec.md) §3 and §6).

```mermaid
flowchart TB
  subgraph Auth["Request authenticated"]
    P[Proxied: verified gateway HMAC]
    N[Non-proxied: validateServiceKey + response HMAC]
  end

  subgraph Outbound["Outbound to usage"]
    R[reportUsage]
    PR[reportProgress]
    C[reportCompletion]
  end

  P --> R
  P --> PR
  P --> C
  N --> R
  N --> PR
  N --> C
```

**SDK value:** **`reportUsage`**, **`reportProgress`**, **`reportCompletion`** produce correctly signed requests and correct URL usage (including full `progress_url` / `callback_url` when the gateway forwarded them for async jobs).

### 3.4 Backend-facing SDK capabilities (summary)

| SDK capability | Proxied agent (gateway in front) | Non-proxied agent (direct to your URL) |
|----------------|----------------------------------|----------------------------------------|
| **verifySignature** / **getUserContext** (gateway HMAC) | **Yes** — required for inbound trust | **No** — gateway does not sign these requests |
| **validateServiceKey** (+ verify core response HMAC) | **Optional** — e.g. double-check, tooling, or custom flows; not required for normal gateway-forwarded traffic | **Yes** — typical for every request (or validated session/cache you define) |
| **reportUsage** / **reportProgress** / **reportCompletion** | **Yes** — when your integration reports to usage | **Yes** — same |

---

## 4. Side-by-side: which services each role talks to

Only **gateway**, **core**, and **usage** are in scope for the public SDK ([sdk-repo-project-context.md](./sdk-repo-project-context.md)). Security-service user JWT flows are **not** part of this SDK surface for service key / HMAC integration.

```mermaid
flowchart TB
  subgraph Caller["Caller"]
    C_GW[Gateway: invoke, optional poll]
    C_CORE[Core: optional validate key]
  end

  subgraph Proxied["Proxied backend"]
    P_IN[Inbound: verify gateway HMAC]
    P_US[Usage: report, progress, complete]
  end

  subgraph NonProxied["Non-proxied backend"]
    N_IN[Inbound: validateServiceKey to core]
    N_US[Usage: report, progress, complete]
  end

  C_GW ~~~ C_CORE
```

| Service | Caller | Proxied backend | Non-proxied backend |
|---------|--------|-----------------|---------------------|
| **Gateway** | Primary (invoke; optional status/result) | Inbound only (gateway calls your URL) | Not used for invoke |
| **Core** | Optional (validate key + HMAC verify response) | Optional `validate` (not required for normal gateway traffic) | **Required** for `validateServiceKey` inbound trust |
| **Usage** | Caller does not sign to usage | Report / progress / completion (service secret) | Same |

---

## 5. Mental model: secrets and keys

- **Service API key** — For **proxied** agents, the caller sends it as `Authorization: Bearer ...` to the **gateway**. For **non-proxied** agents, the caller typically sends the same header to **your** backend; your backend then validates it with **core**.
- **Service secret** — Held by the **agent backend** (and optionally by trusted **caller** code that calls **validate** and must verify response HMAC). Used to **verify** gateway HMAC (**proxied**), to **verify** core validate responses (**non-proxied** and any validate caller), and to **sign** outbound usage/progress/completion requests to **usage**.

Never expose the service secret in browser-only or untrusted caller code; for pure public clients, use invoke-only flows and keep secrets on servers you control.

---

## 6. Implementations and source of truth

- **Normative HTTP and HMAC:** [sdk-api-spec.md](./sdk-api-spec.md).
- **Java reference** in this monorepo: `client/java` (see its README). Published multi-language SDKs live in the separate **tollara-sdk** repository described in [sdk-repo-project-context.md](./sdk-repo-project-context.md).

When diagrams in this doc differ in detail from the spec, **the spec wins**.
