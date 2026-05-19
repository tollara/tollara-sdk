# Package and product naming

**Product name:** Tollara

**Package/artifact prefix:** `tollara` (lowercase) — used for:

- Package names (npm: `@tollara/service-sdk`, NuGet: `Tollara.ServiceSdk`, PyPI: `tollara-service-sdk` (import: `tollara_service_sdk`), Ruby gem: `tollara_service_sdk`, Maven: `com.tollara:service-sdk`, etc.)
- Namespaces, module paths, and human-facing labels in docs

**HTTP headers:** All request/response signatures and user context use the `X-Tollara-*` header family (e.g. `X-Tollara-Signature`, `X-Tollara-Timestamp`, `X-Tollara-User-ID`).
