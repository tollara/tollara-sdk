# Agent Hub SDK (Rust)

**Crate:** `marketplace-agent-sdk` (crates.io)

Verify HMAC, validate agent keys, report usage. HTTP clients behind `http` feature.

## Install

```toml
[dependencies]
marketplace-agent-sdk = "1.0"
# marketplace-agent-sdk = { version = "1.0", features = ["http"] }
```

## Build

```bash
cargo build                    # core only (HMAC, verifier)
cargo build --features http   # with validation & usage HTTP clients
```

## Running tests

Integration tests mock the Agent Hub Core and Usage APIs (see `docs/sdk-api-spec.md`). They require the `http` feature.

```bash
cd sdk-rust
cargo test --features http
```

To run a single test by name:

```bash
cargo test --features http validate_agent_key_returns_result
```

## Example

```rust
use marketplace_agent_sdk::{verify_signature, calculate_hmac, UserContext};

let valid = verify_signature(agent_secret, signature, timestamp, payload, user_id, plan, &roles, quota_remaining);
```

See [HMAC spec](../docs/hmac-spec.md) and [API overview](../docs/api-overview.md).
