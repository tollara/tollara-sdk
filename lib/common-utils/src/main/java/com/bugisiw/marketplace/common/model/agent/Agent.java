package com.bugisiw.marketplace.common.model.agent;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Entity representing an AI Agent in the marketplace.
 * Supports four agent types:
 * - API endpoint agents
 * - MCP servers
 * - No-code agents
 * - Web-button agents
 */
@Entity
@Table(name = "agents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamicInsert
@DynamicUpdate
public class Agent implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private UUID id;

    @NotBlank(message = "Agent name is required")
    @Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters")
    @Column(nullable = false)
    private String name;

    @NotBlank(message = "Description is required")
    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    @Column(nullable = false, length = 1000)
    private String description;

    @NotNull(message = "Agent type is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentType type;

    @NotBlank(message = "Owner ID is required")
    @Column(name = "ext_owner_id", nullable = false)
    private String extOwnerId;

    @Column(name = "mcp_server_url")
    private String mcpServerUrl;

    @Column(name = "nocode_workflow_id")
    private String nocodeWorkflowId;

    @Column(name = "web_button_config", columnDefinition = "TEXT")
    private String webButtonConfig;

    @Column(name = "price_per_request")
    private Double pricePerRequest;

    @Column(name = "monthly_subscription_price")
    private Double monthlySubscriptionPrice;
    
    /**
     * Whether the agent supports hourly billing.
     */
    @Column(name = "supports_hourly_billing")
    private boolean supportsHourlyBilling;
    
    /**
     * URL where callbacks should be sent for asynchronous processing.
     */
    @Column(name = "callback_url")
    private String callbackUrl;

    /**
     * Secret key used for HMAC signature generation to validate requests.
     */
    @Column(name = "agent_secret", length = 64)
    private String agentSecret;

    /**
     * Optional unique slug for branded API domain (e.g. agent-one).
     * When set, used as second subdomain: {ownerSlug}.{apiDomainSlug}.api.yourcompany.com
     */
    @Column(name = "api_domain_slug", unique = true, length = 63)
    private String apiDomainSlug;

    /**
     * API endpoints for this agent.
     * Each endpoint has its own configuration (URL, HTTP method, headers, etc.).
     */
    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference  // Prevents circular reference during JSON serialization
    @Builder.Default
    private List<ApiEndpoint> endpoints = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
    
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
    
    /**
     * Get the supported HTTP methods as a list from all active endpoints.
     * 
     * @return List of supported HTTP methods
     */
    @Transient
    public List<String> getSupportedMethods() {
        if (endpoints == null || endpoints.isEmpty()) {
            return List.of();
        }
        return endpoints.stream()
                .filter(ApiEndpoint::isActive)
                .map(ApiEndpoint::getHttpMethod)
                .distinct()
                .collect(Collectors.toList());
    }
} 