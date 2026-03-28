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

// UserContext from X-AgentVend-* headers.
type UserContext struct {
	UserID             string   `json:"userId"`
	Plan               string   `json:"plan"`
	Roles              []string `json:"roles"`
	QuotaRemaining     *float64 `json:"quotaRemaining"`
	SubscriptionActive bool     `json:"subscriptionActive"`
	BillingModelType   string   `json:"billingModelType,omitempty"`
	MeasurementType    string   `json:"measurementType,omitempty"`
	UnitLabel          string   `json:"unitLabel,omitempty"`
}

// InboundHmacRequest carries material to verify gateway-signed requests.
type InboundHmacRequest struct {
	Signature            string
	Timestamp            string
	Payload              string
	UserID               string
	Plan                 string
	Roles                []string
	QuotaRemaining       string
	SubscriptionActive   bool
	BillingModelType     string
	MeasurementType      string
	UnitLabel            string
}

// BuildGatewayUserContextString builds the HMAC suffix after payload+timestamp.
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

// VerifyInboundHMAC verifies using a single request struct (preferred).
func VerifyInboundHMAC(agentSecret string, req *InboundHmacRequest) bool {
	if req == nil {
		return false
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
	bm := h.Get(HeaderBillingModel)
	mt := h.Get(HeaderMeasurementType)
	ul := h.Get(HeaderUnitLabel)
	req := &InboundHmacRequest{
		Signature:          h.Get(HeaderSignature),
		Timestamp:          h.Get(HeaderTimestamp),
		Payload:            payload,
		UserID:             h.Get(HeaderUserID),
		Plan:               h.Get(HeaderPlan),
		Roles:              splitRolesCSV(h.Get(HeaderRoles)),
		QuotaRemaining:     formatQuotaForSigning(h.Get(HeaderQuotaRemaining)),
		SubscriptionActive:   subActive,
		BillingModelType:   bm,
		MeasurementType:    mt,
		UnitLabel:          ul,
	}
	return VerifyInboundHMAC(agentSecret, req)
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

// VerifySignature validates inbound gateway request HMAC.
func VerifySignature(agentSecret, signature, timestamp, payload string, userID, plan string, roles []string, quotaRemaining string, subscriptionActive bool, billing, measurement, unit string) bool {
	if signature == "" || timestamp == "" || agentSecret == "" {
		return false
	}
	userContextString := BuildGatewayUserContextString(userID, plan, roles, quotaRemaining, subscriptionActive, billing, measurement, unit)
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
		Plan:               h.Get(HeaderPlan),
		Roles:              splitRolesCSV(h.Get(HeaderRoles)),
		QuotaRemaining:     q,
		SubscriptionActive: sub == "true" || sub == "1",
		BillingModelType:   h.Get(HeaderBillingModel),
		MeasurementType:    h.Get(HeaderMeasurementType),
		UnitLabel:          h.Get(HeaderUnitLabel),
	}
}
