# AgentVend SDK (Rust)

**Crate:** `agentvend-agent-sdk` (crates.io)

HMAC verification, user context parsing, and (with the `http` feature) Core validation, Usage reporting, progress/completion, and gateway job polling.

## Configuration (base URLs)

**`AgentVendClient`:** the API origin defaults to **`https://api.agentvend.api`** (`DEFAULT_API_URL`). Set `api_url` or **`AGENTVEND_API_URL`** only to override. Default path prefixes follow [sdk-api-spec.md](../docs/sdk-api-spec.md); lower-level modules still take explicit bases. Use full `progress_url` / `callback_url` for async flows.

See [api-overview.md](../docs/api-overview.md).

## Install

```toml
[dependencies]
agentvend-agent-sdk = "1.0"
# agentvend-agent-sdk = { version = "1.0", features = ["http"] }
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
use agentvend_agent_sdk::{
    verify_inbound_hmac, verify_signature_from_headers, parse_user_context,
    InboundHmacVerify, SignedUserContext,
};

let ok = verify_signature_from_headers(secret, &headers_map, payload);
let ctx = parse_user_context(&headers_map);
```

### HTTP clients (`--features http`)

```rust
use agentvend_agent_sdk::agent_vend_client::{AgentVendClient, AgentVendClientConfig};

let client = AgentVendClient::try_new(AgentVendClientConfig {
    agent_id: Some("agent-uuid".into()),
    agent_secret: Some("secret".into()),
    ..Default::default()
})?;

// Or `try_from_env()` with AGENTVEND_AGENT_SECRET (and optional URL overrides).

client.validate_agent_key(agent_key).await;
client.report_usage(user_id, agent_id, 1.0).await?;
let (ok, status, body) = client.get_request_status(request_id, agent_key).await?;
```

Lower-level modules:

```rust
use agentvend_agent_sdk::validation_client;
use agentvend_agent_sdk::usage_client;
use agentvend_agent_sdk::gateway_client;

// validate_agent_key(&client, core_base_url, agent_key, agent_secret, agent_id)
// report_usage / report_usage_at (optional usage_path_prefix); report_progress_simple; report_completion*, CompletionStatus
let (ok, status, body) = gateway_client::get_request_status(
    &client,
    "https://api.agentvend.api",
    "/api",
    request_id,
    agent_key,
).await?;
```

## Tests

```bash
cargo test
cargo test --features http
```

See [HMAC spec](../docs/hmac-spec.md) and [API spec](../docs/sdk-api-spec.md).
