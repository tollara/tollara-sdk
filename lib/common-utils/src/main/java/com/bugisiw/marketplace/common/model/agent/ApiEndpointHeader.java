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
 * Entity representing a header required for an API endpoint.
 */
@Entity
@Table(name = "api_endpoint_headers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiEndpointHeader implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private UUID id;

    @NotNull(message = "API endpoint is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_endpoint_id", nullable = false)
    private ApiEndpoint apiEndpoint;

    @NotBlank(message = "Header name is required")
    @Size(max = 255, message = "Header name cannot exceed 255 characters")
    @Column(name = "header_name", nullable = false)
    private String headerName;

    @NotBlank(message = "Header value is required")
    @Size(max = 1000, message = "Header value cannot exceed 1000 characters")
    @Column(name = "header_value", nullable = false, length = 1000)
    private String headerValue;

    @Column(name = "is_required", nullable = false)
    @Builder.Default
    private boolean isRequired = true;

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

