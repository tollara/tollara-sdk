package sdk

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
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
}

// VerifySignature validates inbound gateway request HMAC.
func VerifySignature(agentSecret, signature, timestamp, payload string, userID, plan string, roles []string, quotaRemaining string) bool {
	if signature == "" || timestamp == "" || agentSecret == "" {
		return false
	}
	userContextString := userID + plan + strings.Join(roles, ",") + quotaRemaining
	dataToSign := payload + timestamp + userContextString
	expected := CalculateHmac(dataToSign, agentSecret)
	return ConstantTimeEquals(expected, signature)
}

// GetUserContext parses headers into UserContext (caller should pass header map).
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
