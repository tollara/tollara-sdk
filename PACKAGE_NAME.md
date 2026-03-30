# Package and product naming

**Product name:** AgentVend (business name; capital V in "AgentVend").

**Package/artifact prefix:** `agentvend` (lowercase) — used for:

- Package names (npm: `@agentvend/agent-sdk`, NuGet: `AgentVend.AgentSdk`, PyPI: `agentvend-sdk` (import: `agentvend_sdk`), Ruby gem: `agentvend_sdk`, Maven: `com.agentvend:agent-sdk`, etc.)
- Namespaces, module paths, and human-facing labels in docs

**HTTP headers:** All request/response signatures and user context use the `X-AgentVend-*` header family (e.g. `X-AgentVend-Signature`, `X-AgentVend-Timestamp`, `X-AgentVend-User-ID`).
