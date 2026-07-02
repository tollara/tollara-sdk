package sdk

// Canonical Tollara HTTP header names.
const (
	HeaderSignature           = "X-Tollara-Signature"
	HeaderTimestamp           = "X-Tollara-Timestamp"
	HeaderUserID              = "X-Tollara-User-ID"
	HeaderServiceProductID    = "X-Tollara-Service-Product-ID"
	HeaderRoles               = "X-Tollara-Roles"
	HeaderSubscriptionStatus  = "X-Tollara-Subscription-Status"
	HeaderBillingModel        = "X-Tollara-Billing-Model"
	HeaderMeasurementType     = "X-Tollara-Measurement-Type"
	HeaderUnitLabel           = "X-Tollara-Unit-Label"
	HeaderSigningVersion      = "X-Tollara-Signing-Version"
	SigningVersionV3          = "3"

	// Deprecated v1/v2 headers; retained for legacy HMAC verification.
	HeaderPlan               = "X-Tollara-Plan"
	HeaderQuotaRemaining     = "X-Tollara-Quota-Remaining"
	HeaderSubscriptionActive = "X-Tollara-Subscription-Active"
)
