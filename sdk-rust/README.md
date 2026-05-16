# Tollara SDK (Rust)

**Crate:** `tollara-service-sdk` (crates.io)

HMAC verification, user context parsing, and (with the `http` feature) Core validation, Usage reporting, progress/completion, and gateway job polling.

## Configuration (base URLs)

**`TollaraClient`:** the API origin defaults to **`https://api.tollara.ai`** (`DEFAULT_API_URL`). Set `api_url` or **`TOLLARA_API_URL`** only to override. Use full `progress_url` / `callback_url` for async flows.

Use this README as the public usage reference.

### Spec alignment (this crate)

- **Implemented:** HMAC verification, Core validate/service-key estimate/JWT estimate, gateway invoke, usage report/progress/completion, gateway status/result polling (with the `http` feature).
- **Usage report signing:** JSON body uses ISO-8601 `timestamp`; `X-Tollara-Timestamp` uses Unix epoch seconds; canonical HMAC is `bodyJson + headerTimestamp`.

## Install

```toml
[dependencies]
tollara-service-sdk = "1.0"
# tollara-service-sdk = { version = "1.0", features = ["http"] }
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
use tollara_service_sdk::{
    verify_inbound_hmac, verify_signature_from_headers, parse_user_context,
    InboundHmacVerify, SignedUserContext,
};

let ok = verify_signature_from_headers(secret, &headers_map, payload);
let ctx = parse_user_context(&headers_map);
```

### HTTP clients (`--features http`)

```rust
use tollara_service_sdk::tollara_client::{TollaraClient, TollaraClientConfig};
use tollara_service_sdk::gateway_client::GatewayHttpMethod;

let client = TollaraClient::try_new(TollaraClientConfig {
    service_id: Some("service-uuid".into()),
    service_secret: Some("secret".into()),
    ..Default::default()
})?;

// Or `try_from_env()` with TOLLARA_SERVICE_SECRET (and optional URL overrides).

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

See this README for public SDK usage details.
