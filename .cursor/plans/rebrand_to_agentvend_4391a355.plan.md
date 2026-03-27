---
name: Rebrand to AgentVend
overview: Replace all references to agent-hub, bugisiw, and Marketplace with "agentvend" (packages/namespaces) or "AgentVend" (business name and HTTP headers). This includes Java package renames, all SDK package/namespace renames, HTTP header renames to X-AgentVend-*, n8n/OpenClaw integration renames, and documentation updates.
todos: []
isProject: false
---

# Rebrand to AgentVend

## Naming conventions

- **Package/namespace/artifact IDs:** `agentvend` (lowercase), e.g. `com.agentvend.client`, `@agentvend/agent-sdk`, `agentvend-agent-sdk`.
- **Business name in docs and labels:** `AgentVend` (capital V).
- **HTTP headers:** `X-AgentVend-Signature`, `X-AgentVend-Timestamp`, `X-AgentVend-User-ID`, `X-AgentVend-Plan`, `X-AgentVend-Roles`, `X-AgentVend-Quota-Remaining`, `X-AgentVend-Subscription-Active`.

**Important:** The platform/gateway that sends and reads these headers must be updated to use `X-AgentVend-`* in sync with this SDK; otherwise verification will break.

---

## 1. Java (sdk-java)

- **Package layout:** Move sources from `com/bugisiw/marketplace/` to `com/agentvend/`:
  - `client` → `com/agentvend/client`
  - `client/model` → `com/agentvend/client/model`
  - `common/util` → `com/agentvend/common/util`
- **Package declarations and imports:** Change every `com.bugisiw.marketplace.`* to `com.agentvend.`* in all 9 Java files (main + test).
- **Class rename:** `MarketplaceRequestVerifier` → `AgentvendRequestVerifier` (or `RequestVerifier` under `com.agentvend.client`); update all references and [sdk-java/README.md](sdk-java/README.md) examples.
- **Headers:** Replace `X-Marketplace-Signature`, `X-Marketplace-Timestamp` with `X-AgentVend-Signature`, `X-AgentVend-Timestamp` in:
  - [AgentKeyValidationClient.java](sdk-java/src/main/java/com/bugisiw/marketplace/client/AgentKeyValidationClient.java)
  - [UsageServiceClient.java](sdk-java/src/main/java/com/bugisiw/marketplace/client/UsageServiceClient.java)
  - [MarketplaceRequestVerifier.java](sdk-java/src/main/java/com/bugisiw/marketplace/client/MarketplaceRequestVerifier.java) (comments + any header reads if present)
  - Integration tests (expectations and mock responses).
- **Build:** [build.gradle](sdk-java/build.gradle): `group = 'com.agentvend'`, `groupId = 'com.agentvend'`; update repo URL from `agent-hub-sdk` to `agentvend-sdk` (or your chosen repo name).
- **README:** Update package name to `com.agentvend:agent-sdk`, example imports to `com.agentvend.client.`*.

---

## 2. JavaScript/TypeScript (sdk-js)

- **Package:** [package.json](sdk-js/package.json): `name`: `@agentvend/agent-sdk`; keywords: remove `agent-hub`, `marketplace`, add `agentvend`.
- **Headers:** In [verifier.ts](sdk-js/src/verifier.ts), [usageClient.ts](sdk-js/src/usageClient.ts), [validationClient.ts](sdk-js/src/validationClient.ts): replace all `X-Marketplace-`* with `X-AgentVend-`* (including type definitions and string literals).
- **Tests:** [verifier.test.ts](sdk-js/src/verifier.test.ts): update header names in test payloads.
- **README:** Update install and usage to `@agentvend/agent-sdk`.

---

## 3. C# (sdk-dotnet)

- **Namespace:** Replace `Marketplace.AgentSdk` with `AgentVend` (or `AgentVend.AgentSdk` if you prefer) in all `.cs` files: [Hmac.cs](sdk-dotnet/Hmac.cs), [Verifier.cs](sdk-dotnet/Verifier.cs), [ValidationClient.cs](sdk-dotnet/ValidationClient.cs), [UsageClient.cs](sdk-dotnet/UsageClient.cs).
- **Project:** [Marketplace.AgentSdk.csproj](sdk-dotnet/Marketplace.AgentSdk.csproj): rename file to `AgentVend.AgentSdk.csproj` (or keep csproj name and set `PackageId` to `AgentVend.AgentSdk`); set `PackageId` to `AgentVend.AgentSdk`.
- **Headers:** Replace every `X-Marketplace-`* with `X-AgentVend-`* in Verifier.cs, ValidationClient.cs, UsageClient.cs.
- **README:** Update to `AgentVend.AgentSdk` and `using AgentVend;` (or `AgentVend.AgentSdk`).

---

## 4. Python (sdk-python)

- **Package/module name:** Rename directory `src/marketplace_agent_sdk` → `src/agentvend_agent_sdk`; update [pyproject.toml](sdk-python/pyproject.toml) `name = "agentvend-agent-sdk"` and package discovery.
- **Imports:** In all Python files under `src/agentvend_agent_sdk/` and in `tests/`: replace `marketplace_agent_sdk` with `agentvend_agent_sdk`.
- **Headers:** In [verifier.py](sdk-python/src/marketplace_agent_sdk/verifier.py), [usage_client.py](sdk-python/src/marketplace_agent_sdk/usage_client.py), [validation_client.py](sdk-python/src/marketplace_agent_sdk/validation_client.py): replace `X-Marketplace-`* with `X-AgentVend-`* (and lowercase variants for case-insensitive lookups: `x-agentvend-`*).
- **Tests:** Update [test_usage_client_integration.py](sdk-python/tests/test_usage_client_integration.py), [test_validation_client_integration.py](sdk-python/tests/test_validation_client_integration.py), `tests/__init__.py` for new module name and header strings.
- **CI:** [.github/workflows/ci.yml](.github/workflows/ci.yml): update `from marketplace_agent_sdk import ...` to `from agentvend_agent_sdk import ...`.
- **README:** Update package name and install commands to `agentvend-agent-sdk`; remove egg-info from git or regenerate after rename.

---

## 5. Go (sdk-go)

- **Module:** [go.mod](sdk-go/go.mod): change module path from `github.com/your-org/agent-sdk-go` to e.g. `github.com/agentvend/agent-sdk-go` (or your actual org/repo).
- **Headers:** In [hmac.go](sdk-go/hmac.go) (and any other files that reference headers): replace `X-Marketplace-`* with `X-AgentVend-`* in comments and string literals.
- **README:** Update module path and any Agent Hub / marketplace wording to AgentVend.

---

## 6. Rust (sdk-rust)

- **Crate:** [Cargo.toml](sdk-rust/Cargo.toml): `name = "agentvend-agent-sdk"`; update description to reference AgentVend.
- **Headers:** In [validation_client.rs](sdk-rust/src/validation_client.rs), [usage_client.rs](sdk-rust/src/usage_client.rs): replace `X-Marketplace-`* with `X-AgentVend-`*.
- **Tests:** [tests/integration.rs](sdk-rust/tests/integration.rs): update crate name to `agentvend_agent_sdk` and header names.
- **README:** Update crate name and install to `agentvend-agent-sdk`.

---

## 7. Ruby (sdk-ruby)

- **Gem:** Rename [marketplace_agent_sdk.gemspec](sdk-ruby/marketplace_agent_sdk.gemspec) to `agentvend_agent_sdk.gemspec`; set `s.name = "agentvend_agent_sdk"`.
- **Lib:** Rename `lib/marketplace_agent_sdk.rb` to `lib/agentvend_agent_sdk.rb` and update module/require paths; update any header strings to `X-AgentVend-`* if present.
- **README:** Update gem name and install to `agentvend_agent_sdk`.

---

## 8. PHP (sdk-php)

- **Composer:** [composer.json](sdk-php/composer.json): `name`: `agentvend/agent-sdk`; PSR-4: `AgentVend\\AgentSdk\\` → `src/`.
- **Namespace:** Replace `Marketplace\AgentSdk` with `AgentVend\AgentSdk` in all PHP files under `src/`.
- **Headers:** If any PHP code reads/sets `X-Marketplace-`*, switch to `X-AgentVend-`*.
- **README:** Update package and namespace to AgentVend.

---

## 9. n8n integration (integration-n8n)

- **Package:** [package.json](integration-n8n/package.json): `name`: `n8n-nodes-agentvend`; description and keywords: AgentVend; repo URLs: remove `agent-hub-sdk`/`your-org` in favor of agentvend repo.
- **Credentials:** Rename `MarketplaceApi.credentials.ts` → `AgentvendApi.credentials.ts`; update credential name from `marketplaceApi` to `agentvendApi` and all node references.
- **Nodes:** Rename each node folder and file:
  - `MarketplaceTrigger` → `AgentvendTrigger`
  - `MarketplaceInvoke` → `AgentvendInvoke`
  - `MarketplaceProgress` → `AgentvendProgress`
  - `MarketplaceComplete` → `AgentvendComplete`
  - `MarketplaceValidateKey` → `AgentvendValidateKey`
- Update node `name`, `displayName`, `defaults.name`, credential reference, and icon path (e.g. `file:agentvend.svg` — add or rename icon).
- **Headers:** In trigger/node code that forwards headers, use `X-AgentVend-`*.
- **SDK dependency:** In package.json, point to `@agentvend/agent-sdk` (e.g. `file:../sdk-js`).
- **README:** Describe as AgentVend (Agent Vend) and update package name.

---

## 10. OpenClaw integration (integration-openclaw)

- **Package:** [package.json](integration-openclaw/package.json): `name`: `openclaw-agentvend`; description and keywords: AgentVend; dependency: `@agentvend/agent-sdk`.
- **Code:** [backendHandler.ts](integration-openclaw/src/backendHandler.ts) and any other files: replace `X-Marketplace-`* with `X-AgentVend-`*.
- **Skill:** Rename `skills/marketplace/` to `skills/agentvend/`; update [SKILL.md](integration-openclaw/skills/marketplace/SKILL.md) content to reference AgentVend and new package/tool names.
- **Plugin manifest:** [openclaw.plugin.json](integration-openclaw/openclaw.plugin.json): update name/ID to agentvend.
- **README:** Replace Marketplace / Agent Hub with AgentVend; update package name and examples to `openclaw-agentvend`.

---

## 11. Documentation and repo root

- **Specs:** In [docs/hmac-spec.md](docs/hmac-spec.md) and [docs/sdk-api-spec.md](docs/sdk-api-spec.md): replace all `X-Marketplace-`* with `X-AgentVend-`*; replace "Agent Hub" / "marketplace" with "AgentVend" where appropriate.
- **Context:** [docs/sdk-repo-project-context.md](docs/sdk-repo-project-context.md): replace agent-hub, marketplace, your-org with AgentVend / agentvend and new repo URLs.
- **Plans:** In [.cursor/plans/](.cursor/plans/) and [docs/main_website_sdk_and_integrations_docs_aba198ed.plan.md](docs/main_website_sdk_and_integrations_docs_aba198ed.plan.md): replace placeholder and repo names with AgentVend/agentvend (optional; can be left as historical).
- **Root README:** [README.md](README.md): product name AgentVend; table of SDKs with new package names (`com.agentvend:agent-sdk`, `@agentvend/agent-sdk`, `AgentVend.AgentSdk`, `agentvend-agent-sdk`, etc.); remove `your-org` / `agent-hub` links or point to agentvend repo.
- **PACKAGE_NAME.md:** Update or remove; if kept, state that the product name is AgentVend and package prefix is agentvend.

---

## 12. Order and testing

- **Suggested order:** (1) Java (package move + renames + headers), (2) shared docs/specs (hmac-spec, sdk-api-spec), (3) other SDKs (JS, C#, Python, Go, Rust, Ruby, PHP), (4) n8n and OpenClaw integrations, (5) root README and PACKAGE_NAME.
- **Verification:** After each SDK: build and run tests. For Java, run Gradle; for Python, run pytest with new module path; for Node, run tests; etc. CI (`.github/workflows/ci.yml`) should be updated for any renamed packages/modules and re-run to confirm.

---

## Summary of renames


| Area                 | From                                              | To                                                 |
| -------------------- | ------------------------------------------------- | -------------------------------------------------- |
| Java packages        | `com.bugisiw.marketplace.`*                       | `com.agentvend.`*                                  |
| Java class           | `MarketplaceRequestVerifier`                      | `AgentvendRequestVerifier`                         |
| Maven group          | `com.marketplace`                                 | `com.agentvend`                                    |
| npm package          | `@marketplace/agent-sdk`                          | `@agentvend/agent-sdk`                             |
| NuGet / C# namespace | `Marketplace.AgentSdk`                            | `AgentVend` (or `AgentVend.AgentSdk`)              |
| Python package       | `marketplace-agent-sdk` / `marketplace_agent_sdk` | `agentvend-agent-sdk` / `agentvend_agent_sdk`      |
| Go module            | `github.com/your-org/agent-sdk-go`                | `github.com/agentvend/agent-sdk-go` (or your repo) |
| Rust crate           | `marketplace-agent-sdk`                           | `agentvend-agent-sdk`                              |
| Ruby gem             | `marketplace_agent_sdk`                           | `agentvend_agent_sdk`                              |
| PHP package          | `marketplace/agent-sdk`, `Marketplace\AgentSdk`   | `agentvend/agent-sdk`, `AgentVend\AgentSdk`        |
| n8n package/nodes    | `n8n-nodes-marketplace`, `Marketplace`*           | `n8n-nodes-agentvend`, `Agentvend`*                |
| OpenClaw package     | `openclaw-marketplace`                            | `openclaw-agentvend`                               |
| HTTP headers         | `X-Marketplace-`*                                 | `X-AgentVend-*`                                    |


No open questions; proceed with implementation when you approve this plan.