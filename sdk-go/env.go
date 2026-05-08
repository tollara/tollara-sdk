package sdk

// Environment variable names aligned with the Java AgentVendClient (for documentation and callers).
const (
	EnvAPIURL      = "AGENTVEND_API_URL"
	EnvServiceID     = "AGENTVEND_SERVICE_ID"
	EnvServiceSecret = "AGENTVEND_SERVICE_SECRET"
	// Backward-compatible aliases.
	EnvAgentID     = "AGENTVEND_AGENT_ID"
	EnvAgentSecret = "AGENTVEND_AGENT_SECRET"
)

// DefaultAPIURL is the production API origin used by unified clients in other SDKs when no URL is configured.
// This module does not read the environment; use EnvAPIURL in your config loader when you need an override.
const DefaultAPIURL = "https://api.agentvend.api"
