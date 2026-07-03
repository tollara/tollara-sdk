# Tollara SDK (Rust)

**Crate:** `tollara-service-sdk` (crates.io)

HMAC verification, user context parsing, and (with the `http` feature) Core validation, Usage reporting, progress/completion, and gateway job polling.

## Configuration

**`TollaraClient`** uses built-in production defaults. Use full `progress_url` / `callback_url` for async flows.

### Spec alignment (this crate)

- **Implemented:** HMAC verification, Core validate/service-key estimate/JWT estimate, gateway invoke, usage report/progress/completion, gateway status/result polling (with the `http` feature).
- **Usage report signing:** JSON body uses ISO-8601 `timestamp`; `X-Tollara-Timestamp` uses Unix epoch seconds; canonical HMAC is `bodyJson + headerTimestamp`.

## Install

```toml
[dependencies]
tollara-service-sdk = "3.0"
# tollara-service-sdk = { version = "3.0", features = ["http"] }
```

## Build

```bash
cargo build
cargo build --features http
```

## Examples

### Verify HMAC (no HTTP feature)

Verification uses HMAC user-context **v3** when `X-Tollara-Signing-Version` is `"3"`; **v2** when `"2"`; legacy v1 otherwise. Use `grant_access(subscription_status)` for invoke eligibility.

```rust
use std::collections::HashMap;
use tollara_service_sdk::{
    verify_inbound_hmac, verify_signature_from_headers, parse_user_context, grant_access,
    build_gateway_user_context_string_v3, InboundHmacVerify, SignedUserContext,
};

let ok = verify_signature_from_headers(secret, &headers_map, payload);
if grant_access(ctx.subscription_status.as_deref()) { /* invoke-eligible */ }
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

client.validate_service_key(service_key).await; // validationSchemaVersion 3
let est = client.estimate_usage(service_key, 1.0).await; // breakdown.remaining_credits / remaining_spending_cap
let rep = client.report_usage(user_id, service_id, 1.0).await?; // reportSchemaVersion 2 + breakdown
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
