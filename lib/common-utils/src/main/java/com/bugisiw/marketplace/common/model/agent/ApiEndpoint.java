package com.bugisiw.marketplace.common.model.agent;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing an API endpoint for an agent.
 * Each agent can have multiple endpoints, each with its own configuration.
 */
@Entity
@Table(name = "api_endpoints")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiEndpoint implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private UUID id;

    @NotNull(message = "Agent is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    @JsonBackReference  // Prevents circular reference during JSON serialization
    private Agent agent;

    @NotBlank(message = "Endpoint name is required")
    @Size(max = 255, message = "Name cannot exceed 255 characters")
    @Column(nullable = false)
    private String name;

    /**
     * Full target URL including path (e.g. http://host/api/orders/{id}).
     * The path portion is the path template used for branded API routing and matching.
     */
    @NotBlank(message = "Real URL is required")
    @Size(max = 1000, message = "URL cannot exceed 1000 characters")
    @Column(name = "real_url", nullable = false, length = 1000)
    private String realUrl;

    @NotBlank(message = "HTTP method is required")
    @Size(max = 10, message = "HTTP method cannot exceed 10 characters")
    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    @Column(name = "request_body_template", columnDefinition = "TEXT")
    private String requestBodyTemplate;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "is_async", nullable = false)
    @Builder.Default
    @JsonProperty("isAsync")
    private boolean isAsync = false;

    @OneToMany(mappedBy = "apiEndpoint", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ApiEndpointHeader> headers = new ArrayList<>();

    @OneToMany(mappedBy = "apiEndpoint", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ApiEndpointRequestParam> requestParams = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

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
}

