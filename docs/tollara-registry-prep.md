# Tollara registry preparation

Checklist before first public publish under the Tollara name (deprecate AgentVend artifacts after GA).

| Registry | Claim / create | New package ID |
|----------|----------------|----------------|
| Maven Central | Namespace `com.tollara` on [Sonatype Central Portal](https://central.sonatype.com/) | `com.tollara:service-sdk` |
| npm | Org `@tollara` | `@tollara/service-sdk` |
| NuGet | Prefix `Tollara.*` | `Tollara.ServiceSdk` |
| PyPI | Project name | `tollara-service-sdk` |
| Go | Module path on GitHub | `github.com/tollara/service-sdk-go` |
| crates.io | Crate name | `tollara-service-sdk` |
| RubyGems | Gem name | `tollara_service_sdk` |
| Packagist | Vendor/package | `tollara/service-sdk` |
| n8n community | Package | `n8n-nodes-tollara` |
| OpenClaw | Package | `openclaw-tollara` |

**Deprecation:** Mark previous `agentvend` / `AgentVend` packages as deprecated on each registry with a README pointing to the Tollara equivalent. Do not publish further AgentVend versions after Tollara GA.
