package sdk

// Environment variable names aligned with the Java TollaraClient (for documentation and callers).
const (
	EnvAPIURL      = "TOLLARA_API_URL"
	EnvServiceID     = "TOLLARA_SERVICE_ID"
	EnvServiceSecret = "TOLLARA_SERVICE_SECRET"
)

// DefaultAPIURL is the production API origin used by unified clients in other SDKs when no URL is configured.
// This module does not read the environment; use EnvAPIURL in your config loader when you need an override.
const DefaultAPIURL = "https://api.tollara.ai"
