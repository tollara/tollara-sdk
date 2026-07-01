package com.tollara.client;

import java.net.URI;

/**
 * Resolves service path prefixes for hosted Tollara API origins vs local Docker defaults.
 * See {@code docs-sdk/MAIN-SDK-API-SPEC.md} (ECS vs default deployment).
 */
public final class TollaraPathPrefixes {

    public static final String ECS_CORE_PATH_PREFIX = "/core/api/v1";
    public static final String ECS_GATEWAY_PATH_PREFIX = "/gateway/api/v1";
    public static final String ECS_USAGE_PATH_PREFIX = "/usage/api/v1";

    private TollaraPathPrefixes() {
    }

    public static boolean isHostedTollaraApiOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            return false;
        }
        try {
            String host = URI.create(origin.trim()).getHost();
            if (host == null) {
                return false;
            }
            host = host.toLowerCase();
            if ("api.tollara.ai".equals(host) || host.endsWith(".api.tollara.ai")) {
                return true;
            }
            return "api.ppe.tollara.ai".equals(host) || host.endsWith(".api.ppe.tollara.ai");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static String resolveGatewayPathPrefix(String baseUrl, String override) {
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        String origin = resolveOrigin(baseUrl);
        return isHostedTollaraApiOrigin(origin) ? ECS_GATEWAY_PATH_PREFIX : TollaraClient.DEFAULT_GATEWAY_PATH_PREFIX;
    }

    public static String resolveCorePathPrefix(String baseUrl, String override) {
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        String origin = resolveOrigin(baseUrl);
        return isHostedTollaraApiOrigin(origin) ? ECS_CORE_PATH_PREFIX : TollaraClient.DEFAULT_CORE_PATH_PREFIX;
    }

    public static String resolveUsagePathPrefix(String baseUrl, String override) {
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        String origin = resolveOrigin(baseUrl);
        return isHostedTollaraApiOrigin(origin) ? ECS_USAGE_PATH_PREFIX : TollaraClient.DEFAULT_USAGE_PATH_PREFIX;
    }

    private static String resolveOrigin(String baseUrl) {
        String trimmed = baseUrl != null ? baseUrl.trim() : "";
        if (!trimmed.isEmpty()) {
            return TollaraUrls.trimTrailingSlashes(trimmed);
        }
        return TollaraClient.DEFAULT_API_URL;
    }
}
