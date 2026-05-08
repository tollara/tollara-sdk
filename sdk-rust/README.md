# AgentVend SDK (Rust)

**Crate:** `agentvend-service-sdk` (crates.io)

HMAC verification, user context parsing, and (with the `http` feature) Core validation, Usage reporting, progress/completion, and gateway job polling.

## Configuration (base URLs)

**`AgentVendClient`:** the API origin defaults to **`https://api.agentvend.api`** (`DEFAULT_API_URL`). Set `api_url` or **`AGENTVEND_API_URL`** only to override. Default path prefixes match [**MAIN-SDK-API-SPEC.md**](../docs-sdk/MAIN-SDK-API-SPEC.md) (Core `/api/v1`, Gateway `/api`, Usage `/api/usage`). Use full `progress_url` / `callback_url` for async flows.

See [api-overview.md](../docs/api-overview.md) for high-level service roles.

### Spec alignment (this crate)

- **Implemented:** HMAC verification, Core validate/service-key estimate/JWT estimate, gateway invoke, usage report/progress/completion, gateway status/result polling (with the `http` feature).
- **Usage report signing:** JSON body uses ISO-8601 `timestamp`; `X-AgentVend-Timestamp` uses Unix epoch seconds; canonical HMAC is `bodyJson + headerTimestamp`.

## Install

```toml
[dependencies]
agentvend-service-sdk = "1.0"
# agentvend-service-sdk = { version = "1.0", features = ["http"] }
```

## Build

```bash
cargo build
cargo build --features http
```

## Examples

### Verify HMAC (no HTTP feature)

```rust
use std::collections::HashMap;
use agentvend_service_sdk::{
    verify_inbound_hmac, verify_signature_from_headers, parse_user_context,
    InboundHmacVerify, SignedUserContext,
};

let ok = verify_signature_from_headers(secret, &headers_map, payload);
let ctx = parse_user_context(&headers_map);
```

### HTTP clients (`--features http`)

```rust
use agentvend_service_sdk::agent_vend_client::{AgentVendClient, AgentVendClientConfig};
use agentvend_service_sdk::gateway_client::GatewayHttpMethod;

let client = AgentVendClient::try_new(AgentVendClientConfig {
    service_id: Some("service-uuid".into()),
    service_secret: Some("secret".into()),
    ..Default::default()
})?;

// Or `try_from_env()` with AGENTVEND_SERVICE_SECRET (and optional URL overrides).

client.validate_service_key(service_key).await;
client.estimate_usage(service_key, 1.0).await;
client.estimate_usage_with_jwt(bearer_jwt, core_user_id, "service-uuid", 1.0).await;
client.report_usage(user_id, service_id, 1.0).await?;
let (ok, status, body) = client.get_request_status(request_id, service_key).await?;
let (status, body) = client
    .invoke_service(
        GatewayHttpMethod::Post,
        "service-uuid",
        "endpoint-uuid",
        service_key,
        Some(r#"{"input":"value"}"#),
        false,
    )
    .await?;
```

## Tests

```bash
cargo test
cargo test --features http
```

See [HMAC spec](../docs/hmac-spec.md) and [**MAIN-SDK-API-SPEC.md**](../docs-sdk/MAIN-SDK-API-SPEC.md).
