package sdk

import (
	"net/url"
	"strings"
)

const (
	EcsCorePathPrefix    = "/core/api/v1"
	EcsGatewayPathPrefix = "/gateway/api/v1"
	EcsUsagePathPrefix   = "/usage/api/v1"
)

// IsHostedTollaraApiOrigin reports whether origin is hosted Tollara (prod/PPE/branded *.api.tollara.ai).
func IsHostedTollaraApiOrigin(origin string) bool {
	origin = strings.TrimSpace(origin)
	if origin == "" {
		return false
	}
	u, err := url.Parse(origin)
	if err != nil || u.Host == "" {
		return false
	}
	host := strings.ToLower(u.Hostname())
	if host == "api.tollara.ai" || strings.HasSuffix(host, ".api.tollara.ai") {
		return true
	}
	return host == "api.ppe.tollara.ai" || strings.HasSuffix(host, ".api.ppe.tollara.ai")
}

func resolveOrigin(baseURL string) string {
	trimmed := strings.TrimSpace(baseURL)
	if trimmed != "" {
		return trimTrailingSlash(trimmed)
	}
	return DefaultAPIURL
}

// ResolveGatewayPathPrefix selects ECS vs Docker gateway prefix from base URL (explicit override wins).
func ResolveGatewayPathPrefix(baseURL, override string) string {
	if strings.TrimSpace(override) != "" {
		return strings.TrimSpace(override)
	}
	origin := resolveOrigin(baseURL)
	if IsHostedTollaraApiOrigin(origin) {
		return EcsGatewayPathPrefix
	}
	return DefaultGatewayPathPrefix
}

// ResolveCorePathPrefix selects ECS vs Docker core prefix from base URL (explicit override wins).
func ResolveCorePathPrefix(baseURL, override string) string {
	if strings.TrimSpace(override) != "" {
		return strings.TrimSpace(override)
	}
	origin := resolveOrigin(baseURL)
	if IsHostedTollaraApiOrigin(origin) {
		return EcsCorePathPrefix
	}
	return DefaultCorePathPrefix
}

// ResolveUsagePathPrefix selects ECS vs Docker usage prefix from base URL (explicit override wins).
func ResolveUsagePathPrefix(baseURL, override string) string {
	if strings.TrimSpace(override) != "" {
		return strings.TrimSpace(override)
	}
	origin := resolveOrigin(baseURL)
	if IsHostedTollaraApiOrigin(origin) {
		return EcsUsagePathPrefix
	}
	return DefaultUsagePathPrefix
}
