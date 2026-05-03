package com.agentvend.client;

import com.agentvend.client.model.CompletionStatus;
import com.agentvend.client.model.UsageReportResponse;

import com.agentvend.client.model.UsageEstimateResult;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Instant;

/**
 * Single entry point for AgentVend HTTP APIs (Core validate and estimates, Usage report/progress/complete, Gateway
 * invoke and polling).
 * <p>
 * The API origin defaults to {@value #DEFAULT_API_URL} when neither {@link Builder#apiUrl(String)} nor {@value #ENV_API_URL} is set.
 * {@link Builder#agentId(String)} / {@link Builder#agentSecret(String)} or {@value #ENV_AGENT_ID} / {@value #ENV_AGENT_SECRET}
 * (secret required for signing and Core response verification).
 * Default path prefixes match {@code docs-sdk/MAIN-SDK-API-SPEC.md} (default deployment); override with
 * {@link Builder#corePathPrefix(String)}, {@link Builder#gatewayPathPrefix(String)}, {@link Builder#usagePathPrefix(String)}
 * for ECS or local Docker layouts.
 * </p>
 */
public final class AgentVendClient {

    /** Production API origin; used when neither {@link Builder#apiUrl(String)} nor {@value #ENV_API_URL} is set. */
    public static final String DEFAULT_API_URL = "https://api.agentvend.api";

    /**
     * Environment variable holding the API origin (e.g. staging). Optional override when {@link Builder#apiUrl(String)} is not set.
     */
    public static final String ENV_API_URL = "AGENTVEND_API_URL";

    /**
     * Environment variable for the agent UUID (optional; sent to Core on validate when set).
     * Used when {@link Builder#agentId(String)} is not set.
     */
    public static final String ENV_AGENT_ID = "AGENTVEND_AGENT_ID";

    /**
     * Environment variable for the agent shared secret (required for Usage HMAC and Core validate response verification).
     * Used when {@link Builder#agentSecret(String)} is not set.
     */
    public static final String ENV_AGENT_SECRET = "AGENTVEND_AGENT_SECRET";

    /** Default {@code /api/v1} (Core servlet context). */
    public static final String DEFAULT_CORE_PATH_PREFIX = "/api/v1";

    /** Default {@code /api} (Gateway controller path). */
    public static final String DEFAULT_GATEWAY_PATH_PREFIX = "/api";

    /** Default {@code /api/usage} (Usage report/progress/complete root). */
    public static final String DEFAULT_USAGE_PATH_PREFIX = UsageServiceClient.DEFAULT_USAGE_PATH_PREFIX;

    private final String gatewayBaseUrl;
    private final String gatewayPathPrefix;
    private final HttpClient httpClient;
    private final AgentKeyValidationClient core;
    private final UsageServiceClient usage;
    private final GatewayClient gateway;

    private AgentVendClient(Builder b) {
        String resolved = firstNonBlank(b.apiUrl, System.getenv(ENV_API_URL));
        resolved = AgentVendUrls.trimTrailingSlashes(resolved);
        if (resolved.isEmpty()) {
            resolved = DEFAULT_API_URL;
        }

        String coreBase = AgentVendUrls.trimTrailingSlashes(firstNonBlank(b.coreApiUrl, resolved));
        String gwBase = AgentVendUrls.trimTrailingSlashes(firstNonBlank(b.gatewayApiUrl, resolved));
        String usageBase = AgentVendUrls.trimTrailingSlashes(firstNonBlank(b.usageApiUrl, resolved));

        String corePrefix = b.corePathPrefix != null ? b.corePathPrefix : DEFAULT_CORE_PATH_PREFIX;
        this.gatewayPathPrefix = b.gatewayPathPrefix != null ? b.gatewayPathPrefix : DEFAULT_GATEWAY_PATH_PREFIX;
        String usagePrefix = b.usagePathPrefix != null ? b.usagePathPrefix : DEFAULT_USAGE_PATH_PREFIX;

        String coreRoot = AgentVendUrls.join(coreBase, corePrefix);
        this.gatewayBaseUrl = gwBase;
        HttpClient httpClient = b.httpClient != null ? b.httpClient : HttpClient.newHttpClient();
        this.httpClient = httpClient;

        String resolvedAgentId = firstNonBlank(b.agentId, System.getenv(ENV_AGENT_ID));
        String resolvedAgentSecret = firstNonBlank(b.agentSecret, System.getenv(ENV_AGENT_SECRET));
        if (resolvedAgentSecret.isEmpty()) {
            throw new IllegalStateException(
                    "Agent secret is required: set Builder.agentSecret(...) or environment variable " + ENV_AGENT_SECRET);
        }

        this.core = new AgentKeyValidationClient(coreRoot, emptyToNull(resolvedAgentId), resolvedAgentSecret, httpClient);
        this.usage = new UsageServiceClient(usageBase, usagePrefix, resolvedAgentSecret, httpClient);
        this.gateway = new GatewayClient(httpClient);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.trim().isEmpty()) {
            return a.trim();
        }
        if (b != null && !b.trim().isEmpty()) {
            return b.trim();
        }
        return "";
    }

    /** Empty string becomes null so Core JSON omits optional agent id. */
    private static String emptyToNull(String s) {
        return s.isEmpty() ? null : s;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Validates an agent key (Core). Result is cached for a short TTL inside the client.
     */
    public AgentKeyValidationClient.AgentKeyValidationResult validateAgentKey(String agentKey) {
        return core.validateAgentKey(agentKey);
    }

    /**
     * Usage pre-check for an agent key (Core {@code /agent-keys/estimate-usage}). See {@link AgentKeyValidationClient#estimateUsage(String, BigDecimal)}.
     */
    public UsageEstimateResult estimateUsage(String agentKey, BigDecimal estimatedUnits) {
        return core.estimateUsage(agentKey, estimatedUnits);
    }

    /**
     * Core JWT usage estimate ({@code POST …/billing/usage/estimate}). Not HMAC-signed; requires a Bearer JWT.
     */
    public UsageEstimateResult estimateUsageWithJwt(
            String bearerToken, String userId, String agentId, BigDecimal estimatedUnits) {
        return core.estimateUsageWithJwt(bearerToken, userId, agentId, estimatedUnits);
    }

    public void clearValidationCache() {
        core.clearCache();
    }

    public UsageReportResponse reportUsage(String userId, String agentId, BigDecimal unitsUsed) {
        return usage.reportUsage(userId, agentId, unitsUsed);
    }

    public UsageReportResponse reportUsage(String userId, String agentId, BigDecimal unitsUsed, Instant timestamp) {
        return usage.reportUsage(userId, agentId, unitsUsed, timestamp);
    }

    public boolean sendProgressUpdate(String progressUrl, String requestId, String stage, int percentageComplete) {
        return usage.sendProgressUpdate(progressUrl, requestId, stage, percentageComplete);
    }

    public boolean sendProgressUpdate(
            String progressUrl, String requestId, String stage, int percentageComplete, String errorMessage) {
        return usage.sendProgressUpdate(progressUrl, requestId, stage, percentageComplete, errorMessage);
    }

    public boolean sendCompletion(String callbackUrl, String requestId, CompletionStatus status, BigDecimal units) {
        return usage.sendCompletion(callbackUrl, requestId, status, units);
    }

    public boolean sendCompletion(
            String callbackUrl, String requestId, CompletionStatus status, String result, BigDecimal units) {
        return usage.sendCompletion(callbackUrl, requestId, status, result, units);
    }

    public boolean sendCompletion(
            String callbackUrl,
            String requestId,
            CompletionStatus status,
            String result,
            String resultUrl,
            String contentType,
            BigDecimal units) {
        return usage.sendCompletion(callbackUrl, requestId, status, result, resultUrl, contentType, units);
    }

    /**
     * Poll async job status (Gateway). Uses the configured API URL and gateway path prefix.
     */
    public GatewayHttpResponse getRequestStatus(String requestId, String agentKey) {
        return gateway.getRequestStatus(gatewayBaseUrl, gatewayPathPrefix, requestId, agentKey);
    }

    /**
     * Poll async job result (Gateway).
     */
    public GatewayHttpResponse getRequestResult(String requestId, String agentKey) {
        return gateway.getRequestResult(gatewayBaseUrl, gatewayPathPrefix, requestId, agentKey);
    }

    /**
     * Gateway agent invoke (sync or async). See platform spec §1.1–1.2.
     *
     * @param httpMethod    GET, POST, PUT, or DELETE
     * @param requestBody   optional JSON body (POST/PUT); may be null for GET/DELETE
     * @param async         when true, POST/GET to {@code …/invoke/async}
     */
    public GatewayInvokeResult invokeAgent(
            String httpMethod,
            String agentId,
            String endpointId,
            String agentKey,
            String requestBody,
            boolean async) {
        try {
            return GatewayInvokeClient.invoke(
                    httpClient,
                    gatewayBaseUrl,
                    gatewayPathPrefix,
                    httpMethod,
                    agentId,
                    endpointId,
                    agentKey,
                    requestBody,
                    async);
        } catch (IOException e) {
            throw new AgentVendHttpException("Gateway invoke failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AgentVendHttpException("Gateway invoke interrupted", e);
        }
    }

    public static final class Builder {
        private String apiUrl;
        private String coreApiUrl;
        private String gatewayApiUrl;
        private String usageApiUrl;
        private String corePathPrefix;
        private String gatewayPathPrefix;
        private String usagePathPrefix;
        private String agentId;
        private String agentSecret;
        private HttpClient httpClient;

        private Builder() {
        }

        /**
         * Explicit API origin (overrides {@value AgentVendClient#ENV_API_URL}). When omitted and env is unset, {@value AgentVendClient#DEFAULT_API_URL} is used.
         */
        public Builder apiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
            return this;
        }

        /** When non-null, Core requests use this host instead of {@link #apiUrl(String)} / env. */
        public Builder coreApiUrl(String coreApiUrl) {
            this.coreApiUrl = coreApiUrl;
            return this;
        }

        /** When non-null, Gateway polling uses this host instead of {@link #apiUrl(String)} / env. */
        public Builder gatewayApiUrl(String gatewayApiUrl) {
            this.gatewayApiUrl = gatewayApiUrl;
            return this;
        }

        /** When non-null, Usage report/progress/completion base uses this host instead of {@link #apiUrl(String)} / env. */
        public Builder usageApiUrl(String usageApiUrl) {
            this.usageApiUrl = usageApiUrl;
            return this;
        }

        public Builder corePathPrefix(String corePathPrefix) {
            this.corePathPrefix = corePathPrefix;
            return this;
        }

        public Builder gatewayPathPrefix(String gatewayPathPrefix) {
            this.gatewayPathPrefix = gatewayPathPrefix;
            return this;
        }

        public Builder usagePathPrefix(String usagePathPrefix) {
            this.usagePathPrefix = usagePathPrefix;
            return this;
        }

        /**
         * Agent UUID (overrides {@value AgentVendClient#ENV_AGENT_ID}).
         * Optional if Core can infer the agent from the key alone.
         */
        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        /**
         * Shared secret (overrides {@value AgentVendClient#ENV_AGENT_SECRET}).
         * Required after merge with the environment; used for Usage signing and Core response HMAC verification.
         */
        public Builder agentSecret(String agentSecret) {
            this.agentSecret = agentSecret;
            return this;
        }

        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public AgentVendClient build() {
            return new AgentVendClient(this);
        }
    }
}
