package com.bugisiw.marketplace.common.model.agent;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a request parameter for an API endpoint.
 * Used for query parameters in GET requests or other parameterized endpoints.
 */
@Entity
@Table(name = "api_endpoint_request_params")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiEndpointRequestParam implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private UUID id;

    @NotNull(message = "API endpoint is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_endpoint_id", nullable = false)
    private ApiEndpoint apiEndpoint;

    @NotBlank(message = "Parameter name is required")
    @Size(max = 255, message = "Parameter name cannot exceed 255 characters")
    @Column(name = "param_name", nullable = false)
    private String paramName;

    @Size(max = 1000, message = "Parameter value cannot exceed 1000 characters")
    @Column(name = "param_value", length = 1000)
    private String paramValue;

    @Column(name = "is_required", nullable = false)
    @Builder.Default
    private boolean isRequired = false;

    @Size(max = 50, message = "Parameter type cannot exceed 50 characters")
    @Column(name = "param_type", length = 50)
    private String paramType;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    @Column(length = 1000)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

