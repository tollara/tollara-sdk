package sdk

import "testing"

func TestIsHostedTollaraApiOrigin(t *testing.T) {
	if !IsHostedTollaraApiOrigin("https://api.tollara.ai") {
		t.Fatal("expected api.tollara.ai hosted")
	}
	if !IsHostedTollaraApiOrigin("https://acme.api.tollara.ai") {
		t.Fatal("expected branded prod hosted")
	}
	if !IsHostedTollaraApiOrigin("https://api.ppe.tollara.ai") {
		t.Fatal("expected ppe hosted")
	}
	if IsHostedTollaraApiOrigin("http://host.docker.internal:8083") {
		t.Fatal("expected docker origin not hosted")
	}
}

func TestResolveGatewayPathPrefix_HostedProd(t *testing.T) {
	if got := ResolveGatewayPathPrefix("https://api.tollara.ai", ""); got != EcsGatewayPathPrefix {
		t.Fatalf("got %q want %q", got, EcsGatewayPathPrefix)
	}
	if got := ResolveGatewayPathPrefix("", ""); got != EcsGatewayPathPrefix {
		t.Fatalf("default origin got %q want %q", got, EcsGatewayPathPrefix)
	}
	if got := ResolveGatewayPathPrefix("http://host.docker.internal:8083", ""); got != DefaultGatewayPathPrefix {
		t.Fatalf("docker got %q want %q", got, DefaultGatewayPathPrefix)
	}
}

func TestResolveGatewayPathPrefix_ExplicitOverride(t *testing.T) {
	if got := ResolveGatewayPathPrefix("https://api.tollara.ai", "/api"); got != "/api" {
		t.Fatalf("override got %q", got)
	}
}

func TestResolveCoreAndUsagePathPrefixes_HostedProd(t *testing.T) {
	if got := ResolveCorePathPrefix("https://api.tollara.ai", ""); got != EcsCorePathPrefix {
		t.Fatalf("core got %q want %q", got, EcsCorePathPrefix)
	}
	if got := ResolveUsagePathPrefix("https://api.tollara.ai", ""); got != EcsUsagePathPrefix {
		t.Fatalf("usage got %q want %q", got, EcsUsagePathPrefix)
	}
}

func TestNewTollaraClient_UsesEcsPrefixesForHostedDefaultOrigin(t *testing.T) {
	client, err := NewTollaraClient(TollaraClientOptions{ServiceSecret: "secret"})
	if err != nil {
		t.Fatalf("NewTollaraClient: %v", err)
	}
	if client.GatewayPathPrefix != EcsGatewayPathPrefix {
		t.Fatalf("gateway prefix: %q", client.GatewayPathPrefix)
	}
	if client.CorePathPrefix != EcsCorePathPrefix {
		t.Fatalf("core prefix: %q", client.CorePathPrefix)
	}
	if client.UsagePathPrefix != EcsUsagePathPrefix {
		t.Fatalf("usage prefix: %q", client.UsagePathPrefix)
	}
}
