package com.bugisiw.marketplace.common.model.agent;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing an Agent Owner in the marketplace.
 * Stores Stripe Connect account information and business details for agent owners.
 * One record per agent owner (ext_user_id).
 */
@Entity
@Table(name = "agent_owner")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentOwner implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "ext_user_id", unique = true, nullable = false)
    private String extUserId;

    @Column(name = "stripe_account_id")
    private String stripeAccountId;

    @Column(name = "stripe_onboarding_url")
    private String stripeOnboardingUrl;

    @Column(name = "business_name")
    private String businessName;

    @Column(name = "business_type")
    private String businessType; // INDIVIDUAL or COMPANY

    @Column(name = "business_profile_url")
    private String businessProfileUrl;

    @Column(name = "onboarding_completed_at")
    private Instant onboardingCompletedAt;

    @Column(name = "onboarding_status")
    @Builder.Default
    private String onboardingStatus = "PENDING";

    @Column(name = "onboarding_error_message", columnDefinition = "TEXT")
    private String onboardingErrorMessage;

    @Column(name = "onboarding_error_timestamp")
    private Instant onboardingErrorTimestamp;

    @Column(name = "onboarding_failed_reason")
    private String onboardingFailedReason;

    @Column(name = "onboarding_last_checked_at")
    private Instant onboardingLastCheckedAt;

    @Column(name = "stripe_charges_enabled")
    private Boolean stripeChargesEnabled;

    @Column(name = "stripe_transfers_enabled")
    private Boolean stripeTransfersEnabled;

    /**
     * Unique slug for branded API domain (e.g. agent-owner-123).
     * Used as first subdomain segment: {apiDomainSlug}.api.yourcompany.com
     */
    @Column(name = "api_domain_slug", unique = true, length = 63)
    private String apiDomainSlug;

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
        if (onboardingStatus == null) {
            onboardingStatus = "PENDING";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

