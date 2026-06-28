package sdk

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"net/http"
	"strconv"
	"strings"
)

// CalculateHmac returns Base64(HMAC-SHA256(data, key)) with UTF-8.
func CalculateHmac(data, key string) string {
	mac := hmac.New(sha256.New, []byte(key))
	mac.Write([]byte(data))
	return base64.StdEncoding.EncodeToString(mac.Sum(nil))
}

// CalculateHmacWithTimestamp signs bodyString+timestamp (outbound).
func CalculateHmacWithTimestamp(bodyString, timestamp, key string) string {
	return CalculateHmac(bodyString+timestamp, key)
}

// ConstantTimeEquals avoids timing attacks.
func ConstantTimeEquals(a, b string) bool {
	if len(a) != len(b) {
		return false
	}
	return hmac.Equal([]byte(a), []byte(b))
}

// ValidateHmacSignature verifies signature == HMAC(payloadString, key).
func ValidateHmacSignature(signature, payloadString, key string) bool {
	if signature == "" || key == "" {
		return false
	}
	expected := CalculateHmac(payloadString, key)
	return ConstantTimeEquals(expected, signature)
}

var invokeEligibleStatuses = map[string]struct{}{
	"ACTIVE":              {},
	"TRIAL":               {},
	"CANCELLING":          {},
	"CANCELLING_PENDING":  {},
}

// GrantsAccess returns true when subscriptionStatus is invoke-eligible.
func GrantsAccess(subscriptionStatus string) bool {
	s := strings.ToUpper(strings.TrimSpace(subscriptionStatus))
	if s == "" {
		return false
	}
	_, ok := invokeEligibleStatuses[s]
	return ok
}

// UserContext from X-Tollara-* headers.
type UserContext struct {
	UserID             string   `json:"userId"`
	ServiceProductID   string   `json:"serviceProductId,omitempty"`
	Roles              []string `json:"roles"`
	SubscriptionStatus string   `json:"subscriptionStatus,omitempty"`
	BillingModelType   string   `json:"billingModelType,omitempty"`
	MeasurementType    string   `json:"measurementType,omitempty"`
	UnitLabel          string   `json:"unitLabel,omitempty"`

	// Deprecated v1/v2 fields.
	Plan               string   `json:"plan,omitempty"`
	QuotaRemaining     *float64 `json:"quotaRemaining,omitempty"`
	SubscriptionActive bool     `json:"subscriptionActive,omitempty"`
}

// InboundHmacRequest carries material to verify gateway-signed requests.
type InboundHmacRequest struct {
	Signature          string
	Timestamp          string
	Payload            string
	UserID             string
	ServiceProductID   string
	Roles              []string
	SubscriptionStatus string
	BillingModelType   string
	MeasurementType    string
	UnitLabel          string
	SigningVersion     string

	// Deprecated v1/v2 fields.
	Plan               string
	QuotaRemaining     string
	SubscriptionActive bool
}

// BuildGatewayUserContextString builds the v1 HMAC suffix after payload+timestamp.
func BuildGatewayUserContextString(userID, plan string, roles []string, quotaRemaining string, subscriptionActive bool, billing, measurement, unit string) string {
	var sb strings.Builder
	sb.WriteString(userID)
	sb.WriteString(plan)
	sb.WriteString(strings.Join(roles, ","))
	sb.WriteString(quotaRemaining)
	if subscriptionActive {
		sb.WriteString("true")
	} else {
		sb.WriteString("false")
	}
	sb.WriteString(billing)
	sb.WriteString(measurement)
	sb.WriteString(unit)
	return sb.String()
}

// BuildGatewayUserContextStringV2 builds the v2 HMAC suffix with leading "2" and no quota segment.
func BuildGatewayUserContextStringV2(userID, plan string, roles []string, subscriptionActive bool, billing, measurement, unit string) string {
	var sb strings.Builder
	sb.WriteString("2")
	sb.WriteString(userID)
	sb.WriteString(plan)
	sb.WriteString(strings.Join(roles, ","))
	if subscriptionActive {
		sb.WriteString("true")
	} else {
		sb.WriteString("false")
	}
	sb.WriteString(billing)
	sb.WriteString(measurement)
	sb.WriteString(unit)
	return sb.String()
}

// BuildGatewayUserContextStringV3 builds the v3 HMAC suffix with leading "3", serviceProductId, and subscriptionStatus.
func BuildGatewayUserContextStringV3(userID, serviceProductID string, roles []string, subscriptionStatus, billing, measurement, unit string) string {
	var sb strings.Builder
	sb.WriteString("3")
	sb.WriteString(userID)
	sb.WriteString(serviceProductID)
	sb.WriteString(strings.Join(roles, ","))
	sb.WriteString(subscriptionStatus)
	sb.WriteString(billing)
	sb.WriteString(measurement)
	sb.WriteString(unit)
	return sb.String()
}

// VerifyInboundHMAC verifies using a single request struct (preferred).
func VerifyInboundHMAC(agentSecret string, req *InboundHmacRequest) bool {
	if req == nil {
		return false
	}
	if strings.TrimSpace(req.SigningVersion) == SigningVersionV3 {
		return VerifySignatureV3(
			agentSecret,
			req.Signature,
			req.Timestamp,
			req.Payload,
			req.UserID,
			req.ServiceProductID,
			req.Roles,
			req.SubscriptionStatus,
			req.BillingModelType,
			req.MeasurementType,
			req.UnitLabel,
		)
	}
	if strings.TrimSpace(req.SigningVersion) == "2" {
		return VerifySignatureV2(
			agentSecret,
			req.Signature,
			req.Timestamp,
			req.Payload,
			req.UserID,
			req.Plan,
			req.Roles,
			req.SubscriptionActive,
			req.BillingModelType,
			req.MeasurementType,
			req.UnitLabel,
		)
	}
	return VerifySignature(agentSecret, req.Signature, req.Timestamp, req.Payload, req.UserID, req.Plan, req.Roles, req.QuotaRemaining, req.SubscriptionActive, req.BillingModelType, req.MeasurementType, req.UnitLabel)
}

// VerifyInboundHMACFromHeaders uses net/http Header (case-insensitive lookup).
func VerifyInboundHMACFromHeaders(agentSecret string, h http.Header, payload string) bool {
	if h == nil {
		return false
	}
	sub := h.Get(HeaderSubscriptionActive)
	subActive := sub == "true" || sub == "1"
	req := &InboundHmacRequest{
		Signature:          h.Get(HeaderSignature),
		Timestamp:          h.Get(HeaderTimestamp),
		Payload:            payload,
		UserID:             h.Get(HeaderUserID),
		ServiceProductID:   h.Get(HeaderServiceProductID),
		Roles:              splitRolesCSV(h.Get(HeaderRoles)),
		SubscriptionStatus: h.Get(HeaderSubscriptionStatus),
		BillingModelType:   h.Get(HeaderBillingModel),
		MeasurementType:    h.Get(HeaderMeasurementType),
		UnitLabel:          h.Get(HeaderUnitLabel),
		SigningVersion:     h.Get(HeaderSigningVersion),
		Plan:               h.Get(HeaderPlan),
		QuotaRemaining:     formatQuotaForSigning(h.Get(HeaderQuotaRemaining)),
		SubscriptionActive: subActive,
	}
	return VerifyInboundHMAC(agentSecret, req)
}

// VerifyInboundHMACFromHeadersAndGetUserContext verifies HMAC; if ok is true, ctx is from headers (trusted only when ok).
func VerifyInboundHMACFromHeadersAndGetUserContext(agentSecret string, h http.Header, payload string) (ctx UserContext, ok bool) {
	if !VerifyInboundHMACFromHeaders(agentSecret, h, payload) {
		return UserContext{}, false
	}
	return UserContextFromHeaders(h), true
}

func formatQuotaForSigning(raw string) string {
	if raw == "" {
		return ""
	}
	v, err := strconv.ParseFloat(strings.TrimSpace(raw), 64)
	if err != nil {
		return raw
	}
	if v == float64(int64(v)) {
		return strconv.FormatInt(int64(v), 10)
	}
	return strconv.FormatFloat(v, 'f', -1, 64)
}

func splitRolesCSV(csv string) []string {
	if csv == "" {
		return nil
	}
	var out []string
	for _, s := range strings.Split(csv, ",") {
		if t := strings.TrimSpace(s); t != "" {
			out = append(out, t)
		}
	}
	return out
}

// VerifySignature validates inbound gateway request HMAC (v1).
func VerifySignature(agentSecret, signature, timestamp, payload string, userID, plan string, roles []string, quotaRemaining string, subscriptionActive bool, billing, measurement, unit string) bool {
	if signature == "" || timestamp == "" || agentSecret == "" {
		return false
	}
	userContextString := BuildGatewayUserContextString(userID, plan, roles, quotaRemaining, subscriptionActive, billing, measurement, unit)
	dataToSign := payload + timestamp + userContextString
	expected := CalculateHmac(dataToSign, agentSecret)
	return ConstantTimeEquals(expected, signature)
}

// VerifySignatureV2 validates inbound gateway request HMAC using v2 user context string.
func VerifySignatureV2(agentSecret, signature, timestamp, payload string, userID, plan string, roles []string, subscriptionActive bool, billing, measurement, unit string) bool {
	if signature == "" || timestamp == "" || agentSecret == "" {
		return false
	}
	userContextString := BuildGatewayUserContextStringV2(userID, plan, roles, subscriptionActive, billing, measurement, unit)
	dataToSign := payload + timestamp + userContextString
	expected := CalculateHmac(dataToSign, agentSecret)
	return ConstantTimeEquals(expected, signature)
}

// VerifySignatureV3 validates inbound gateway request HMAC using v3 user context string.
func VerifySignatureV3(agentSecret, signature, timestamp, payload, userID, serviceProductID string, roles []string, subscriptionStatus, billing, measurement, unit string) bool {
	if signature == "" || timestamp == "" || agentSecret == "" {
		return false
	}
	userContextString := BuildGatewayUserContextStringV3(userID, serviceProductID, roles, subscriptionStatus, billing, measurement, unit)
	dataToSign := payload + timestamp + userContextString
	expected := CalculateHmac(dataToSign, agentSecret)
	return ConstantTimeEquals(expected, signature)
}

// GetUserContext parses individual header values (legacy helper).
func GetUserContext(userID, plan, rolesCsv, quotaRemaining, subscriptionActive string) UserContext {
	var roles []string
	if rolesCsv != "" {
		for _, s := range strings.Split(rolesCsv, ",") {
			if t := strings.TrimSpace(s); t != "" {
				roles = append(roles, t)
			}
		}
	}
	var q *float64
	if quotaRemaining != "" {
		if v, err := strconv.ParseFloat(quotaRemaining, 64); err == nil {
			q = &v
		}
	}
	return UserContext{
		UserID:             userID,
		Plan:               plan,
		Roles:              roles,
		QuotaRemaining:     q,
		SubscriptionActive: subscriptionActive == "true" || subscriptionActive == "1",
	}
}

// UserContextFromHeaders parses full user context from http.Header.
func UserContextFromHeaders(h http.Header) UserContext {
	if h == nil {
		return UserContext{}
	}
	qstr := h.Get(HeaderQuotaRemaining)
	var q *float64
	if qstr != "" {
		if v, err := strconv.ParseFloat(qstr, 64); err == nil {
			q = &v
		}
	}
	sub := h.Get(HeaderSubscriptionActive)
	return UserContext{
		UserID:             h.Get(HeaderUserID),
		ServiceProductID:   h.Get(HeaderServiceProductID),
		Roles:              splitRolesCSV(h.Get(HeaderRoles)),
		SubscriptionStatus: h.Get(HeaderSubscriptionStatus),
		BillingModelType:   h.Get(HeaderBillingModel),
		MeasurementType:    h.Get(HeaderMeasurementType),
		UnitLabel:          h.Get(HeaderUnitLabel),
		Plan:               h.Get(HeaderPlan),
		QuotaRemaining:     q,
		SubscriptionActive: sub == "true" || sub == "1",
	}
}
