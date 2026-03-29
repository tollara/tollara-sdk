package com.agentvend.client;

/**
 * Internal URL joining for API bases and path prefixes.
 */
final class AgentVendUrls {

    private AgentVendUrls() {
    }

    static String trimTrailingSlashes(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    /** Join base (scheme/host[/optional path]) with a path segment that may start with {@code /}. */
    static String join(String base, String path) {
        String b = trimTrailingSlashes(base);
        if (path == null || path.isEmpty()) {
            return b;
        }
        String p = path.startsWith("/") ? path : "/" + path;
        return b + p;
    }
}
