# Tollara SDK Monorepo

Client SDKs and integrations for **Tollara** — a platform for marketing, monetizing, and managing AI Agents and MCP Servers.

This monorepo provides:

- **Caller SDKs**: Validate service keys, invoke via the gateway (sync/async).
- **Backend support**: Verify HMAC on incoming gateway requests, extract user context, report usage and (for async) progress/completion.

For API and HMAC details, see [docs/](docs/).

## SDKs and integrations

| Language / integration | Install | Description |
|------------------------|--------|-------------|
| **Java** | `sdk-java/` — see [sdk-java/README.md](sdk-java/README.md) | Maven: `com.tollara:service-sdk` |
| **JavaScript/TypeScript** | `npm install @tollara/service-sdk` | [sdk-js/README.md](sdk-js/README.md) |
| **C#** | `dotnet add package Tollara.ServiceSdk` | [sdk-dotnet/README.md](sdk-dotnet/README.md) |
| **Python** | `pip install tollara-service-sdk` | [sdk-python/README.md](sdk-python/README.md) |
| **Go** | `go get github.com/tollara/service-sdk-go` | [sdk-go/README.md](sdk-go/README.md) |
| **Rust** | `cargo add tollara-service-sdk` | [sdk-rust/README.md](sdk-rust/README.md) |
| **Ruby** | `gem install tollara_service_sdk` | [sdk-ruby/README.md](sdk-ruby/README.md) |
| **PHP** | `composer require tollara/service-sdk` | [sdk-php/README.md](sdk-php/README.md) |
| **n8n** | Install community node from `integration-n8n/` | [integration-n8n/README.md](integration-n8n/README.md) |
| **OpenClaw** | `openclaw plugins install openclaw-tollara` | [integration-openclaw/README.md](integration-openclaw/README.md) |

## Documentation

- [HMAC specification](docs/hmac-spec.md) — signing and verification (all SDKs).
- [API overview](docs/api-overview.md) — gateway, core, and usage endpoints.

## Versioning

See [CHANGELOG.md](CHANGELOG.md). Pre-release development uses **1.0.0**; gateway inbound HMAC uses the extended `userContextString` in [docs/hmac-spec.md](docs/hmac-spec.md).

## Build and test (per folder)

Each `sdk-*` and `integration-*` folder is self-contained. From repo root:

- **sdk-java**: `cd sdk-java && ./gradlew build`
- **sdk-js**: `cd sdk-js && npm ci && npm test`
- **sdk-dotnet**: `cd sdk-dotnet && dotnet build` and `cd sdk-dotnet/Tollara.ServiceSdk.Tests && dotnet test` (when .NET SDK is installed)
- **sdk-python**: `cd sdk-python && pip install -e . && pytest`
- **sdk-go**: `cd sdk-go && go build ./...`
- **sdk-rust**: `cd sdk-rust && cargo build`
- **sdk-ruby**: `cd sdk-ruby && bundle install && bundle exec rspec`
- **sdk-php**: `cd sdk-php && composer install && composer test`
- **integration-n8n**: `cd integration-n8n && npm ci && npm test`
- **integration-openclaw**: `cd integration-openclaw && npm ci && npm test`
