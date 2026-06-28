# SDK contract JSON fixtures

Shared example bodies for **validation v3**, **estimate v3**, and **report v2** aligned with [MAIN-SDK-API-SPEC.md](../MAIN-SDK-API-SPEC.md).

Use these in SDK contract tests (mock HTTP responses) and when updating platform stubs. **Validate** and **estimate** success responses are HMAC-signed in tests as `Base64(HMAC-SHA256(rawBodyUtf8 + X-Tollara-Timestamp, serviceSecret))`.

| File | Schema | Notes |
|------|--------|--------|
| `validate-success-v3.json` | `validationSchemaVersion: 3` | `serviceProductId`, `subscriptionStatus`; no `plan` / `quotaRemaining` / `subscriptionActive` |
| `estimate-success-v3.json` | `estimateSchemaVersion: 3` | Balances/caps on `breakdown` only (no top-level `remainingCredits` / `remainingSpendingCap`) |
| `report-success-v2.json` | `reportSchemaVersion: 2` | Identity + `breakdown`; no legacy top-level quota/limit fields |

Golden HMAC v3 user-context strings live in per-language unit tests (`buildV3` / `build_gateway_user_context_string_v3`).
